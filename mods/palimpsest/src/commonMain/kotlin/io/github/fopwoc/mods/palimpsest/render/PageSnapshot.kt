package io.github.fopwoc.mods.palimpsest.render

import io.github.fopwoc.mods.palimpsest.map.MapPageRaster

/** Owned immutable inputs and output of one build; empty pages retain no sample arrays. */
internal class PageSnapshot(
    val grid: SampleGrid?,
    val raster: MapPageRaster?,
    val shadedPixels: Int,
) {
    companion object {
        val EMPTY = PageSnapshot(null, null, 0)
    }
}
