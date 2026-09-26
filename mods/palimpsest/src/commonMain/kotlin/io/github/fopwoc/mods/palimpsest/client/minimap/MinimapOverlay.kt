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
import io.github.fopwoc.mods.palimpsest.client.map.MapSession
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor

/**
 * The live map around the player in a corner of the screen, north up, from the session's own
 * minimap view. Showing it and its zoom last until the game closes; defaults come from
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

    override val visible: Boolean
        get() {
            val client = ClientBackend.current
            return (shown ?: PalimpsestConfig.minimapEnabled) &&
                client.isInWorld &&
                !client.isHudHidden &&
                MapSessions.session != null
        }

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
        val size = PalimpsestConfig.minimapSize
        val camera = MapCamera(centerX, centerZ, ZOOM_LEVELS[zoom], size, size)
        map.submit(session.map.minimapView.frame(camera))
        val arrow = PlayerMarker.image(client.playerYaw ?: 0f)
        val side = MINIMAP_MARKER_SIZE.toFloat()
        marker.submit(GpuCanvasFrame(listOf(GpuImageDraw(arrow, 0f, 0f, side, side))))
        model =
            MinimapModel(
                screenWidth = width,
                screenHeight = height,
                corner = PalimpsestConfig.minimapCorner,
                size = size,
                coordinates =
                    if (PalimpsestConfig.minimapCoordinates)
                        "${floor(position.x).toInt()}, ${floor(position.y).toInt()}, " +
                            "${floor(position.z).toInt()}"
                    else null,
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

    /** GUI pixels per block, from a quarter to four. */
    private val ZOOM_LEVELS = doubleArrayOf(0.25, 0.5, 1.0, 2.0, 4.0)
    private const val DEFAULT_ZOOM = 2
    /** Farther than a player walks in a tick: a teleport, drawn at once instead of glided. */
    private const val SNAP_BLOCKS = 32.0
    /** About one tick: the glide never trails the player by more than a frame or two. */
    private const val EASE_SECONDS = 0.05
    private const val NANOS_PER_SECOND = 1e9
}
