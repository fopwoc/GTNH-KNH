package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImageDraw
import io.github.fopwoc.mods.palimpsest.client.motion.FrameClock
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import io.github.fopwoc.mods.palimpsest.map.MapPageKey
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import kotlin.math.roundToInt

/**
 * The tiles a snapshot changed, flashed over the map: solid for a moment, then fading out. A canvas
 * frame batches images of one size, so the tint is a page-sized image per opacity step.
 */
class ChangeHighlight(val tiles: List<TileKey>, private val startNanos: Long) {
    init {
        require(tiles.isNotEmpty())
    }

    /** Whether the flash still shows at [nowNanos]. */
    fun visible(nowNanos: Long): Boolean =
        nowNanos - startNanos < (HOLD_SECONDS + FADE_SECONDS) * FrameClock.NANOS_PER_SECOND

    fun draws(camera: MapCamera, nowNanos: Long): List<GpuImageDraw> {
        val elapsed = (nowNanos - startNanos) / FrameClock.NANOS_PER_SECOND
        val opacity = (1 - (elapsed - HOLD_SECONDS) / FADE_SECONDS).coerceIn(0.0, 1.0)
        val step = (opacity * (STEPS - 1)).roundToInt()
        if (step == 0) return emptyList()
        val image = images[step]
        return tiles.map { tile ->
            camera.quad(image, tile.x * TILE_BLOCKS, tile.z * TILE_BLOCKS, TILE_BLOCKS)
        }
    }

    companion object {
        const val HOLD_SECONDS = 2.0
        const val FADE_SECONDS = 1.0
        private const val TILE_BLOCKS = 16.0
        private const val STEPS = 8
        private const val PEAK_ALPHA = 0.55
        private const val RED = 0xFF.toByte()
        private const val GREEN = 0xB0.toByte()
        private const val BLUE = 0x40.toByte()

        private val images: List<GpuImage> by lazy {
            List(STEPS) { step ->
                val alpha = (PEAK_ALPHA * step / (STEPS - 1) * 0xFF).roundToInt().toByte()
                val side = MapPageKey.SIDE
                val rgba = ByteArray(side * side * 4)
                for (pixel in 0 until side * side) {
                    rgba[pixel * 4] = RED
                    rgba[pixel * 4 + 1] = GREEN
                    rgba[pixel * 4 + 2] = BLUE
                    rgba[pixel * 4 + 3] = alpha
                }
                GpuImage(side, side, rgba)
            }
        }
    }
}
