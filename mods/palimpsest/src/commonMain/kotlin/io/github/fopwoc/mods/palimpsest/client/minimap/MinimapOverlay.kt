package io.github.fopwoc.mods.palimpsest.client.minimap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImageDraw
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayer
import io.github.fopwoc.mods.framework.ui.compose.hud.HudPlacement
import io.github.fopwoc.mods.framework.ui.compose.input.KeyBinding
import io.github.fopwoc.mods.palimpsest.client.map.MapSession
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import io.github.fopwoc.mods.palimpsest.client.motion.FrameClock
import io.github.fopwoc.mods.palimpsest.client.motion.GlidingPoint
import io.github.fopwoc.mods.palimpsest.client.motion.easeStep
import io.github.fopwoc.mods.palimpsest.config.MinimapRotation
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * The live map around the player in a corner of the screen, from the session's own minimap view,
 * north up or turning with the player; while [bigMap] is held, a see-through map over most of the
 * screen instead. Showing it and its zoom last until the game closes; defaults come from
 * [PalimpsestConfig]. The centre glides after the player, whose position only moves once a tick,
 * and the scale glides to each new zoom level.
 */
object MinimapOverlay : HudLayer("palimpsest:minimap", HudPlacement.BELOW_DEBUG) {
    private val map = GpuCanvasState(GpuCanvasFrame(emptyList()))
    private val marker = GpuCanvasState(GpuCanvasFrame(emptyList()))
    private val dots = GpuCanvasState(GpuCanvasFrame(emptyList()))
    private val entities = EntityDots()
    private var model by mutableStateOf<MinimapModel?>(null)
    private var shown: Boolean? = null
    private var zoom = DEFAULT_ZOOM
    /** The natural log of the scale on screen, gliding towards [zoom]'s; NaN before a frame. */
    private var shownScale = Double.NaN
    private var followed: MapSession? = null
    private var followedCenter: GlidingPoint? = null
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
        val center = follow(session, position.x, position.z, seconds)
        val pixelsPerBlock = glideZoom(seconds)
        val yaw = client.playerYaw ?: 0f
        val turn =
            if (PalimpsestConfig.minimapRotation == MinimapRotation.PLAYER_UP)
                MapTurn.headingUp(yaw)
            else null
        val layout =
            if (isBig)
                MinimapLayout.Big(
                    (width - 2 * BIG_MAP_SCREEN_MARGIN).coerceAtLeast(1),
                    (height - 2 * BIG_MAP_SCREEN_MARGIN).coerceAtLeast(1),
                )
            else MinimapLayout.Corner(PalimpsestConfig.minimapCorner, PalimpsestConfig.minimapSize)
        val (mapWidth, mapHeight) = layout.mapSize
        val view = session.map.minimapView
        val frame =
            if (turn != null) {
                val cover = ceil(hypot(mapWidth.toDouble(), mapHeight.toDouble())).toInt()
                turned(view.frame(center.camera(cover, cover, pixelsPerBlock)), cover, layout, turn)
            } else {
                view.frame(center.camera(mapWidth, mapHeight, pixelsPerBlock))
            }
        val alpha = if (isBig) PalimpsestConfig.bigMapOpacity / PERCENT else 1f
        map.submit(
            if (alpha < 1f) GpuCanvasFrame(frame.draws.map { it.copy(alpha = alpha) }) else frame
        )
        dots.submit(
            GpuCanvasFrame(
                entities.draws(
                    center.camera(mapWidth, mapHeight, pixelsPerBlock),
                    turn ?: MapTurn.NONE,
                    position.y,
                    seconds,
                )
            )
        )
        val side = MINIMAP_MARKER_SIZE.toFloat()
        val arrowTurn = if (turn != null) 0f else PlayerMarker.rotation(yaw)
        marker.submit(
            GpuCanvasFrame(listOf(GpuImageDraw(PlayerMarker.image, 0f, 0f, side, side, arrowTurn)))
        )
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
                north = turn?.let { northMark(mapWidth, mapHeight, it) },
            )
    }

    private fun GlidingPoint.camera(width: Int, height: Int, pixelsPerBlock: Double) =
        MapCamera(x, z, pixelsPerBlock, width, height)

    /**
     * [frame] of a [source]-pixel square, large enough to cover the map at any angle, turned by
     * [turn] around its centre and re-centred on the map of [layout].
     */
    private fun turned(
        frame: GpuCanvasFrame,
        source: Int,
        layout: MinimapLayout,
        turn: MapTurn,
    ): GpuCanvasFrame {
        val (width, height) = layout.mapSize
        return GpuCanvasFrame(
            frame.draws.map { draw ->
                val dx = draw.x + draw.width / 2.0 - source / 2.0
                val dy = draw.y + draw.height / 2.0 - source / 2.0
                draw.copy(
                    x = (width / 2.0 + turn.x(dx, dy) - draw.width / 2.0).toFloat(),
                    y = (height / 2.0 + turn.y(dx, dy) - draw.height / 2.0).toFloat(),
                    rotation = turn.degrees,
                )
            }
        )
    }

    /**
     * Where the north badge sits on a [width] by [height] map turned by [turn]: the way north from
     * the centre, stopped at the edge so the badge stays whole inside it.
     */
    private fun northMark(width: Int, height: Int, turn: MapTurn): MapMark {
        val dx = turn.x(0.0, -1.0)
        val dy = turn.y(0.0, -1.0)
        val halfWidth = width / 2.0 - NORTH_BADGE_WIDTH / 2.0 - NORTH_INSET
        val halfHeight = height / 2.0 - NORTH_BADGE_HEIGHT / 2.0 - NORTH_INSET
        val reach =
            minOf(
                if (abs(dx) > EPSILON) halfWidth / abs(dx) else Double.MAX_VALUE,
                if (abs(dy) > EPSILON) halfHeight / abs(dy) else Double.MAX_VALUE,
            )
        return MapMark(
            (width / 2.0 + dx * reach).roundToInt(),
            (height / 2.0 + dy * reach).roundToInt(),
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

    /** The map centre, gliding after the player; it starts over in a new session. */
    private fun follow(session: MapSession, x: Double, z: Double, seconds: Double): GlidingPoint {
        val current = followedCenter?.takeIf { session === followed }
        if (current == null) {
            followed = session
            return GlidingPoint(x, 0.0, z).also { followedCenter = it }
        }
        return current.also { it.follow(x, 0.0, z, seconds) }
    }

    @Composable
    override fun Content() {
        model?.let { MinimapView(it, map, dots, marker) }
    }

    private const val PERCENT = 100f
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
