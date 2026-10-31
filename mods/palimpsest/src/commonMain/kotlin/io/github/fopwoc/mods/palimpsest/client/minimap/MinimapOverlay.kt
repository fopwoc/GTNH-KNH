package io.github.fopwoc.mods.palimpsest.client.minimap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImageDraw
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuScene
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayer
import io.github.fopwoc.mods.framework.ui.compose.hud.HudPlacement
import io.github.fopwoc.mods.framework.ui.compose.input.KeyBinding
import io.github.fopwoc.mods.palimpsest.client.claim.ClaimDraws
import io.github.fopwoc.mods.palimpsest.client.claim.ClaimLayer
import io.github.fopwoc.mods.palimpsest.client.map.MapSession
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import io.github.fopwoc.mods.palimpsest.client.motion.FrameClock
import io.github.fopwoc.mods.palimpsest.client.motion.GlidingPoint
import io.github.fopwoc.mods.palimpsest.client.motion.easeStep
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingLayers
import io.github.fopwoc.mods.palimpsest.config.MinimapRotation
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig
import io.github.fopwoc.mods.palimpsest.map.IsoTerrain
import io.github.fopwoc.mods.palimpsest.map.MapTime
import io.github.fopwoc.mods.palimpsest.render.iso.IsoCamera
import io.github.fopwoc.mods.palimpsest.render.iso.MarkProjection
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * The live map around the player in a corner of the screen, seen straight down from the session's
 * own minimap slices, north up or turning with the player; while [bigMap] is held, a see-through
 * map over most of the screen instead. Showing it and its zoom last until the game closes; defaults
 * come from [PalimpsestConfig]. The centre glides after the player, whose position only moves once
 * a tick, and the scale glides to each new zoom level.
 */
object MinimapOverlay : HudLayer("palimpsest:minimap", HudPlacement.BELOW_DEBUG) {
    private val map = GpuCanvasState(GpuCanvasFrame(emptyList()))
    private val marker = GpuCanvasState(GpuCanvasFrame(emptyList()))
    private val claims = GpuCanvasState(GpuCanvasFrame(emptyList()))
    private val dots = GpuCanvasState(GpuCanvasFrame(emptyList()))
    private val entities = EntityDots()
    private var model by mutableStateOf<MinimapModel?>(null)
    private var shown: Boolean? = null
    private var zoom = DEFAULT_ZOOM
    /** The natural log of the scale on screen, gliding towards [zoom]'s; NaN before a frame. */
    private var shownScale = Double.NaN
    private var followed: MapSession? = null
    private var followedCenter: GlidingPoint? = null
    private var terrain: IsoTerrain? = null
    private val clock = FrameClock()

    /** Held for the big map; set once the key is registered. */
    var bigMap: KeyBinding? = null

    override val visible: Boolean
        get() {
            val client = ClientBackend.current
            return ((shown ?: PalimpsestConfig.minimapEnabled) || isBig) &&
                client.isInWorld &&
                !client.isHudHidden &&
                MapSessions.session != null
        }

    private val isBig: Boolean
        get() = bigMap?.isDown == true

    fun toggle() {
        shown = !(shown ?: PalimpsestConfig.minimapEnabled)
    }

    fun zoomIn() {
        zoom = (zoom + 1).coerceAtMost(ZOOM_LEVELS.lastIndex)
    }

    fun zoomOut() {
        zoom = (zoom - 1).coerceAtLeast(0)
    }

    override fun beforeFrame() {
        val client = ClientBackend.current
        val session = MapSessions.session
        val position = client.playerPosition
        if (session == null || position == null) {
            model = null
            entities.clear()
            return
        }
        val seconds = clock.tick(System.nanoTime())
        val center = follow(session, position.x, position.y, position.z, seconds)
        val terrain = checkNotNull(terrain)
        val pixelsPerBlock = glideZoom(seconds)
        val playerYaw = client.playerYaw ?: 0f
        val headingUp = PalimpsestConfig.minimapRotation == MinimapRotation.PLAYER_UP
        val yaw = if (headingUp) HALF_TURN - playerYaw.toDouble() else 0.0
        val layout =
            if (isBig)
                MinimapLayout.Big(
                    (width - 2 * BIG_MAP_SCREEN_MARGIN).coerceAtLeast(1),
                    (height - 2 * BIG_MAP_SCREEN_MARGIN).coerceAtLeast(1),
                )
            else
                MinimapLayout.Corner(
                    PalimpsestConfig.minimapCorner,
                    PalimpsestConfig.minimapSize,
                    PalimpsestConfig.minimapHorizontalPadding,
                    PalimpsestConfig.minimapVerticalPadding,
                )
        val (mapWidth, mapHeight) = layout.mapSize
        val camera =
            IsoCamera(
                center.x,
                center.y,
                center.z,
                pixelsPerBlock,
                yaw,
                IsoCamera.MAX_PITCH,
                mapWidth,
                mapHeight,
            )
        val area = camera.footprint(center.y, center.y)
        if (ClaimLayer.enabled.value) session.requestClaims(area)
        session.minimap.request(area)
        terrain.request(camera, MapTime.Live, null)
        val groundY = { x: Int, z: Int -> terrain.topAt(x, z)?.toDouble() ?: position.y }
        val alpha = if (isBig) PalimpsestConfig.bigMapOpacity / PERCENT else 1f
        map.submit(
            GpuCanvasFrame(
                emptyList(),
                scene = GpuScene(terrain.meshes, camera.sceneCamera(), alpha),
            )
        )
        claims.submit(
            if (ClaimLayer.enabled.value)
                ClaimDraws.frame(camera, groundY, session.claimMarks.value, alpha)
            else GpuCanvasFrame(emptyList())
        )
        dots.submit(GpuCanvasFrame(entities.draws(camera, position.y, seconds)))
        val side = MINIMAP_MARKER_SIZE.toFloat()
        marker.submit(
            GpuCanvasFrame(
                listOf(
                    GpuImageDraw(
                        PlayerMarker.image,
                        0f,
                        0f,
                        side,
                        side,
                        PlayerMarker.rotation(playerYaw) + yaw.toFloat(),
                    )
                )
            )
        )
        val marks = MinimapMarks(MarkProjection(camera, groundY))
        model =
            MinimapModel(
                screenWidth = width,
                screenHeight = height,
                layout = layout,
                coordinates =
                    if (!isBig && PalimpsestConfig.minimapCoordinates)
                        "${floor(position.x).toInt()}, ${floor(position.y).toInt()}, " +
                            "${floor(position.z).toInt()}"
                    else null,
                north = if (headingUp) northMark(camera) else null,
                waypoints = marks.waypoints(session.waypoints.entries.value),
                prospecting =
                    marks.prospecting(
                        session.prospectingMarks.value,
                        ProspectingLayers.enabled.value,
                    ),
                claims =
                    if (ClaimLayer.enabled.value) marks.claims(session.claimMarks.value)
                    else emptyList(),
            )
    }

    /**
     * Where the north badge sits on [camera]'s turned map: the way north from the centre, stopped
     * at the edge so the badge stays whole inside it.
     */
    private fun northMark(camera: IsoCamera): MapMark {
        val dx = camera.screenX(camera.centerX, camera.centerZ - 1) - camera.width / 2.0
        val dy =
            camera.screenY(camera.centerX, camera.centerY, camera.centerZ - 1) - camera.height / 2.0
        val length = hypot(dx, dy)
        val halfWidth = camera.width / 2.0 - NORTH_BADGE_WIDTH / 2.0 - NORTH_INSET
        val halfHeight = camera.height / 2.0 - NORTH_BADGE_HEIGHT / 2.0 - NORTH_INSET
        val reach =
            minOf(
                if (abs(dx) > EPSILON) halfWidth / abs(dx / length) else Double.MAX_VALUE,
                if (abs(dy) > EPSILON) halfHeight / abs(dy / length) else Double.MAX_VALUE,
            )
        return MapMark(
            (camera.width / 2.0 + dx / length * reach).roundToInt(),
            (camera.height / 2.0 + dy / length * reach).roundToInt(),
        )
    }

    /** Moves the shown scale towards the zoom level's, evenly in log space; returns it. */
    private fun glideZoom(seconds: Double): Double {
        val target = ln(ZOOM_LEVELS[zoom])
        shownScale =
            if (shownScale.isNaN() || abs(target - shownScale) < ZOOM_SNAP) target
            else shownScale + (target - shownScale) * easeStep(seconds, ZOOM_EASE_SECONDS)
        return exp(shownScale)
    }

    /**
     * The map centre, gliding after the player; it starts over in a new session, with terrain read
     * from that session's minimap slices.
     */
    private fun follow(
        session: MapSession,
        x: Double,
        y: Double,
        z: Double,
        seconds: Double,
    ): GlidingPoint {
        val current = followedCenter?.takeIf { session === followed }
        if (current == null) {
            followed = session
            terrain?.close()
            terrain = IsoTerrain(null, session.minimap, LIVE_REFRESH_NANOS)
            return GlidingPoint(x, y, z).also { followedCenter = it }
        }
        return current.also { it.follow(x, y, z, seconds) }
    }

    @Composable
    override fun Content() {
        model?.let { MinimapView(it, map, claims, dots, marker) }
    }

    private const val PERCENT = 100f
    /** The scan publishes a batch every few ticks; the minimap shows each one about as fast. */
    private const val LIVE_REFRESH_NANOS = 250_000_000L
    private const val HALF_TURN = 180.0
    /** Gap between the north badge and the map edge. */
    private const val NORTH_INSET = 1
    private const val EPSILON = 1e-9

    /** GUI pixels per block, from a quarter to four. */
    private val ZOOM_LEVELS = doubleArrayOf(0.25, 0.5, 1.0, 2.0, 4.0)
    private const val DEFAULT_ZOOM = 2
    /** How fast the scale settles on a new zoom level: most of the way in a quarter second. */
    private const val ZOOM_EASE_SECONDS = 0.08
    /** Close enough in log scale to stop gliding. */
    private const val ZOOM_SNAP = 1e-3
}
