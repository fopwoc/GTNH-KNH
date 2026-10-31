package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuMesh
import io.github.fopwoc.mods.palimpsest.client.motion.FrameClock
import io.github.fopwoc.mods.palimpsest.render.iso.GroundQuads
import io.github.fopwoc.mods.palimpsest.render.iso.IsoCamera
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import kotlin.math.roundToInt

/** The tiles a snapshot changed, flashed on the ground: solid for a moment, then fading out. */
class ChangeHighlight(val tiles: List<TileKey>, private val startNanos: Long) {
    init {
        require(tiles.isNotEmpty())
    }

    /** Whether the flash still shows at [nowNanos]. */
    fun visible(nowNanos: Long): Boolean =
        nowNanos - startNanos < (HOLD_SECONDS + FADE_SECONDS) * FrameClock.NANOS_PER_SECOND

    fun mesh(camera: IsoCamera, groundY: (Int, Int) -> Double, nowNanos: Long): GpuMesh {
        val elapsed = (nowNanos - startNanos) / FrameClock.NANOS_PER_SECOND
        val opacity = (1 - (elapsed - HOLD_SECONDS) / FADE_SECONDS).coerceIn(0.0, 1.0)
        val alpha = (PEAK_ALPHA * opacity * OPAQUE).roundToInt()
        if (alpha == 0) return GpuMesh.EMPTY
        val quads = GroundQuads(camera, groundY)
        val color = (alpha shl ALPHA_SHIFT) or AMBER
        for (tile in tiles) {
            quads.add(
                tile.x * TILE_BLOCKS,
                tile.z * TILE_BLOCKS,
                (tile.x + 1) * TILE_BLOCKS,
                (tile.z + 1) * TILE_BLOCKS,
                color,
            )
        }
        return quads.build()
    }

    companion object {
        const val HOLD_SECONDS = 2.0
        const val FADE_SECONDS = 1.0
        private const val TILE_BLOCKS = 16.0
        private const val PEAK_ALPHA = 0.55
        private const val OPAQUE = 255
        private const val ALPHA_SHIFT = 24
        private const val AMBER = 0xFFB040
    }
}
