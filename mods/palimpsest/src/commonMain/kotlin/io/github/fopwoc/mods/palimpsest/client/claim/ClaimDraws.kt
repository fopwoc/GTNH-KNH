package io.github.fopwoc.mods.palimpsest.client.claim

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage
import io.github.fopwoc.mods.palimpsest.map.MapCamera

/** Team-colored chunk fills; a strong border denotes a reported force-loaded chunk. */
object ClaimDraws {
    private const val CHUNK_SIDE = 16.0
    private val images = HashMap<Pair<Int, Boolean>, GpuImage>()

    fun frame(camera: MapCamera, marks: List<ClaimMark>): GpuCanvasFrame =
        GpuCanvasFrame(
            marks.mapNotNull { mark ->
                val draw =
                    camera.quad(
                        image(mark),
                        mark.chunkX * CHUNK_SIDE,
                        mark.chunkZ * CHUNK_SIDE,
                        CHUNK_SIDE,
                    )
                if (
                    draw.x + draw.width < 0 ||
                        draw.y + draw.height < 0 ||
                        draw.x >= camera.width ||
                        draw.y >= camera.height
                )
                    null
                else draw
            }
        )

    private fun image(mark: ClaimMark): GpuImage =
        images.getOrPut(mark.color to mark.forceLoaded) {
            val pixels = ByteArray(16 * 16 * 4)
            for (y in 0 until 16) for (x in 0 until 16) {
                val index = (y * 16 + x) * 4
                val border = x == 0 || x == 15 || y == 0 || y == 15
                pixels[index] = (mark.color shr 16).toByte()
                pixels[index + 1] = (mark.color shr 8).toByte()
                pixels[index + 2] = mark.color.toByte()
                pixels[index + 3] =
                    (if (border) if (mark.forceLoaded) 220 else 100 else 55).toByte()
            }
            GpuImage(16, 16, pixels)
        }
}
