package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImageDraw
import kotlin.math.floor
import kotlin.math.log2

/** The viewport uses world pixels; translation and zoom change quads without rebuilding pages. */
data class MapCamera(
    val centerX: Double,
    val centerZ: Double,
    val pixelsPerBlock: Double,
    val width: Int,
    val height: Int,
) {
    init {
        require(centerX.isFinite() && centerZ.isFinite())
        require(pixelsPerBlock.isFinite() && pixelsPerBlock > 0.0)
        require(width > 0 && height > 0)
    }

    val lod: Int = floor(log2(1.0 / pixelsPerBlock)).toInt().coerceIn(0, MapPageKey.MAX_LOD)

    fun visiblePages(): List<MapPageKey> {
        val span = MapPageKey.SIDE.toDouble() * (1 shl lod)
        val left = floor((centerX - width / (2.0 * pixelsPerBlock)) / span).toInt()
        val right = floor((centerX + width / (2.0 * pixelsPerBlock)) / span).toInt()
        val top = floor((centerZ - height / (2.0 * pixelsPerBlock)) / span).toInt()
        val bottom = floor((centerZ + height / (2.0 * pixelsPerBlock)) / span).toInt()
        require((right.toLong() - left + 1) * (bottom.toLong() - top + 1) <= 256) {
            "Viewport requires too many map pages at the current zoom"
        }
        return buildList {
            for (z in top..bottom) for (x in left..right) add(MapPageKey(x, z, lod))
        }
    }

    /** Places a page of any LOD; a stand-in from another level is scaled to its own coverage. */
    fun draw(key: MapPageKey, image: GpuImage): GpuImageDraw {
        val span = MapPageKey.SIDE.toDouble() * (1 shl key.lod)
        return quad(image, key.x * span, key.z * span, span)
    }

    /** Places [image] over the [side]-block square whose corner is at the given world position. */
    fun quad(image: GpuImage, worldX: Double, worldZ: Double, side: Double): GpuImageDraw {
        val size = (side * pixelsPerBlock).toFloat()
        return GpuImageDraw(
            image,
            ((worldX - centerX) * pixelsPerBlock + width / 2.0).toFloat(),
            ((worldZ - centerZ) * pixelsPerBlock + height / 2.0).toFloat(),
            size,
            size,
        )
    }
}
