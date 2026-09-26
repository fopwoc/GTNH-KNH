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
import io.github.fopwoc.mods.framework.ui.compose.input.KeyBinding
import io.github.fopwoc.mods.palimpsest.client.map.MapSession
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import io.github.fopwoc.mods.palimpsest.config.MinimapRotation
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The live map around the player in a corner of the screen, from the session's own minimap view,
 * north up or turning with the player; while [bigMap] is held, a see-through map over most of the
 * screen instead. Showing it and its zoom last until the game closes; defaults come from
 * [PalimpsestConfig]. The centre glides after the player, whose position only moves once a tick.
 */
object MinimapOverlay : HudLayer("palimpsest:minimap") {
    private val map = GpuCanvasState(GpuCanvasFrame(emptyList()))
    private val marker = GpuCanvasState(GpuCanvasFrame(emptyList()))
    private var model by mutableStateOf<MinimapModel?>(null)
    private var shown: Boolean? = null
    private var zoom = DEFAULT_ZOOM
    private var followed: MapSession? = null
    private var centerX = 0.0
    private var centerZ = 0.0
    private var lastFrameNanos = 0L

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
            return
        }
        follow(session, position.x, position.z, System.nanoTime())
        val yaw = client.playerYaw ?: 0f
        val view = session.map.minimapView
        val pixelsPerBlock = ZOOM_LEVELS[zoom]
        val turn = !isBig && PalimpsestConfig.minimapRotation == MinimapRotation.PLAYER_UP
        val layout: MinimapLayout
        if (isBig) {
            val big =
                MinimapLayout.Big(
                    (width - 2 * BIG_MAP_SCREEN_MARGIN).coerceAtLeast(1),
                    (height - 2 * BIG_MAP_SCREEN_MARGIN).coerceAtLeast(1),
                )
            val alpha = PalimpsestConfig.bigMapOpacity / PERCENT
            val camera = MapCamera(centerX, centerZ, pixelsPerBlock, big.width, big.height)
            map.submit(GpuCanvasFrame(view.frame(camera).draws.map { it.copy(alpha = alpha) }))
            layout = big
        } else {
            val size = PalimpsestConfig.minimapSize
            map.submit(
                if (turn) turned(view.frame(squareCamera(size * SQRT_2, pixelsPerBlock)), size, yaw)
                else view.frame(squareCamera(size.toDouble(), pixelsPerBlock))
            )
            layout = MinimapLayout.Corner(PalimpsestConfig.minimapCorner, size)
        }
        val side = MINIMAP_MARKER_SIZE.toFloat()
        val arrowTurn = if (turn) 0f else PlayerMarker.rotation(yaw)
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
                north = if (turn) northMark(PalimpsestConfig.minimapSize, mapTurn(yaw)) else null,
            )
    }

    private fun squareCamera(side: Double, pixelsPerBlock: Double): MapCamera {
        val pixels = ceil(side).toInt()
        return MapCamera(centerX, centerZ, pixelsPerBlock, pixels, pixels)
    }

    /** Clockwise turn of the map that puts the player's heading at the top. */
    private fun mapTurn(yaw: Float): Float = HALF_TURN - yaw

    /**
     * [frame] of a square large enough to cover [size] at any angle, turned around its centre and
     * re-centred on a [size] canvas.
     */
    private fun turned(frame: GpuCanvasFrame, size: Int, yaw: Float): GpuCanvasFrame {
        val degrees = mapTurn(yaw)
        val radians = Math.toRadians(degrees.toDouble())
        val cos = cos(radians)
        val sin = sin(radians)
        val source = ceil(size * SQRT_2).toInt() / 2.0
        val target = size / 2.0
        return GpuCanvasFrame(
            frame.draws.map { draw ->
                val dx = draw.x + draw.width / 2.0 - source
                val dy = draw.y + draw.height / 2.0 - source
                draw.copy(
                    x = (target + dx * cos - dy * sin - draw.width / 2.0).toFloat(),
                    y = (target + dx * sin + dy * cos - draw.height / 2.0).toFloat(),
                    rotation = degrees,
                )
            }
        )
    }

    /** North on a map turned [degrees] clockwise, just inside its edge. */
    private fun northMark(size: Int, degrees: Float): MapMark {
        val radians = Math.toRadians(degrees.toDouble())
        val reach = size / 2.0 - NORTH_INSET
        return MapMark(
            (size / 2.0 + sin(radians) * reach).roundToInt(),
            (size / 2.0 - cos(radians) * reach).roundToInt(),
        )
    }

    /** Eases the centre towards the player; jumps on a new session or a teleport. */
    private fun follow(session: MapSession, x: Double, z: Double, nowNanos: Long) {
        val jump =
            session !== followed || abs(x - centerX) > SNAP_BLOCKS || abs(z - centerZ) > SNAP_BLOCKS
        if (jump) {
            followed = session
            centerX = x
            centerZ = z
        } else {
            val seconds = (nowNanos - lastFrameNanos).coerceAtLeast(0) / NANOS_PER_SECOND
            val step = 1 - exp(-seconds / EASE_SECONDS)
            centerX += (x - centerX) * step
            centerZ += (z - centerZ) * step
        }
        lastFrameNanos = nowNanos
    }

    @Composable
    override fun Content() {
        model?.let { MinimapView(it, map, marker) }
    }

    private const val SQRT_2 = 1.4143
    private const val HALF_TURN = 180f
    private const val PERCENT = 100f
    private const val NORTH_INSET = 6

    /** GUI pixels per block, from a quarter to four. */
    private val ZOOM_LEVELS = doubleArrayOf(0.25, 0.5, 1.0, 2.0, 4.0)
    private const val DEFAULT_ZOOM = 2
    /** Farther than a player walks in a tick: a teleport, drawn at once instead of glided. */
    private const val SNAP_BLOCKS = 32.0
    /** About one tick: the glide never trails the player by more than a frame or two. */
    private const val EASE_SECONDS = 0.05
    private const val NANOS_PER_SECOND = 1e9
}
