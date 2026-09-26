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

    /**
     * The level whose pages land 64 to 128 GUI pixels wide, or a coarser one when the viewport is
     * so large in GUI pixels (GUI scale 1 on a big screen) that those would be too many pages.
     */
    val lod: Int = run {
        var level = floor(log2(1.0 / pixelsPerBlock)).toInt().coerceIn(0, MapPageKey.MAX_LOD)
        while (level < MapPageKey.MAX_LOD && pageRange(level).count > MAX_PAGES) level++
        level
    }

    /** At most [MAX_PAGES]; past the coarsest level, the ones nearest the centre. */
    fun visiblePages(): List<MapPageKey> {
        val range = pageRange(lod).let { if (it.count > MAX_PAGES) it.around(centerPage()) else it }
        val pages = buildList {
            for (z in range.top..range.bottom) for (x in range.left..range.right) {
                add(MapPageKey(x, z, lod))
            }
        }
        if (pages.size <= MAX_PAGES) return pages
        val span = MapPageKey.SIDE.toDouble() * (1 shl lod)
        return pages
            .sortedBy { key ->
                val dx = (key.x + 0.5) * span - centerX
                val dz = (key.z + 0.5) * span - centerZ
                dx * dx + dz * dz
            }
            .take(MAX_PAGES)
    }

    private fun centerPage(): Pair<Int, Int> {
        val span = MapPageKey.SIDE.toDouble() * (1 shl lod)
        return floor(centerX / span).toInt() to floor(centerZ / span).toInt()
    }

    private fun pageRange(level: Int): PageRange {
        val span = MapPageKey.SIDE.toDouble() * (1 shl level)
        return PageRange(
            left = floor((centerX - width / (2.0 * pixelsPerBlock)) / span).toInt(),
            right = floor((centerX + width / (2.0 * pixelsPerBlock)) / span).toInt(),
            top = floor((centerZ - height / (2.0 * pixelsPerBlock)) / span).toInt(),
            bottom = floor((centerZ + height / (2.0 * pixelsPerBlock)) / span).toInt(),
        )
    }

    private class PageRange(val left: Int, val right: Int, val top: Int, val bottom: Int) {
        val count: Long
            get() = (right.toLong() - left + 1) * (bottom.toLong() - top + 1)

        /** This range cut to a square just big enough for [MAX_PAGES] around [center]. */
        fun around(center: Pair<Int, Int>): PageRange {
            val (x, z) = center
            return PageRange(
                maxOf(left, x - HALF_WINDOW),
                minOf(right, x + HALF_WINDOW),
                maxOf(top, z - HALF_WINDOW),
                minOf(bottom, z + HALF_WINDOW),
            )
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

    private companion object {
        /** Pages a frame may ask for; each is built, cached and drawn. */
        const val MAX_PAGES = 256

        /** Pages each side of the centre one when cutting a range; 17 by 17 covers [MAX_PAGES]. */
        const val HALF_WINDOW = 8
    }
}
