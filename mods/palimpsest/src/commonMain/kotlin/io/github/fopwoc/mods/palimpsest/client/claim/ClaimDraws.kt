package io.github.fopwoc.mods.palimpsest.client.claim

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.palimpsest.render.iso.GroundQuads
import io.github.fopwoc.mods.palimpsest.render.iso.IsoCamera

/**
 * Team-coloured chunk fills lying on the ground, a block-wide border round each; a strong border
 * denotes a reported force-loaded chunk.
 */
object ClaimDraws {
    private const val CHUNK_SIDE = 16.0
    private const val BORDER = 1.0
    private const val FILL_ALPHA = 55
    private const val BORDER_ALPHA = 100
    private const val FORCED_BORDER_ALPHA = 220
    private const val ALPHA_SHIFT = 24
    private const val RGB = 0xFFFFFF

    fun frame(
        camera: IsoCamera,
        groundY: (Int, Int) -> Double,
        marks: List<ClaimMark>,
        alpha: Float = 1f,
    ): GpuCanvasFrame {
        val quads = GroundQuads(camera, groundY)
        for (mark in marks) {
            val x0 = mark.chunkX * CHUNK_SIDE
            val z0 = mark.chunkZ * CHUNK_SIDE
            val x1 = x0 + CHUNK_SIDE
            val z1 = z0 + CHUNK_SIDE
            val fill = tint(mark.color, FILL_ALPHA, alpha)
            val border =
                tint(mark.color, if (mark.forceLoaded) FORCED_BORDER_ALPHA else BORDER_ALPHA, alpha)
            val y = quads.groundAt(x0 + CHUNK_SIDE / 2, z0 + CHUNK_SIDE / 2)
            quads.add(x0 + BORDER, z0 + BORDER, x1 - BORDER, z1 - BORDER, fill, y)
            quads.add(x0, z0, x1, z0 + BORDER, border, y)
            quads.add(x0, z1 - BORDER, x1, z1, border, y)
            quads.add(x0, z0 + BORDER, x0 + BORDER, z1 - BORDER, border, y)
            quads.add(x1 - BORDER, z0 + BORDER, x1, z1 - BORDER, border, y)
        }
        return GpuCanvasFrame(emptyList(), quads.build())
    }

    private fun tint(color: Int, alpha: Int, fade: Float): Int =
        ((alpha * fade).toInt() shl ALPHA_SHIFT) or (color and RGB)
}
