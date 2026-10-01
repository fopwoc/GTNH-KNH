package io.github.fopwoc.mods.palimpsest.render

import io.github.fopwoc.mods.palimpsest.map.MapPageRaster

/** Exact facts select relief-dependent patches; dense changes use one full shader pass. */
internal class PageShading(private val shader: TerrainShader) {
    fun build(grid: SampleGrid, previous: PageSnapshot?): PageSnapshot {
        val before = previous?.grid
        if (before == null) {
            val rgba = ByteArray(grid.side * grid.side * 4)
            shader.shade(grid, rgba)
            return PageSnapshot(grid, MapPageRaster(rgba), grid.side * grid.side)
        }
        require(before.side == grid.side && grid.side % PATCH == 0)
        val across = grid.side / PATCH
        val dirty = BooleanArray(across * across)
        fun mark(x: Int, z: Int) {
            if (x in 0 until grid.side && z in 0 until grid.side)
                dirty[z / PATCH * across + x / PATCH] = true
        }
        for (at in grid.block.indices) {
            if (
                grid.block[at] == before.block[at] &&
                    (grid.block[at] == SampleGrid.NONE ||
                        (grid.height[at] == before.height[at] &&
                            grid.depth[at] == before.depth[at] &&
                            grid.biome[at] == before.biome[at]))
            )
                continue
            val x = at % grid.stride - 1
            val z = at / grid.stride - 1
            mark(x, z)
            mark(x + 1, z)
            mark(x, z + 1)
        }
        val count = dirty.count { it }
        if (count == 0) return PageSnapshot(grid, previous.raster, 0)
        val full = count * 2 >= dirty.size
        val rgba =
            if (full) ByteArray(grid.side * grid.side * 4)
            else checkNotNull(previous.raster).copyPixels()
        if (full) shader.shade(grid, rgba)
        else
            for (patch in dirty.indices) if (dirty[patch]) {
                val x = patch % across * PATCH
                val z = patch / across * PATCH
                shader.shade(grid, rgba, x, z, x + PATCH, z + PATCH)
            }
        val raster = MapPageRaster(rgba)
        val stable = previous.raster?.takeIf { it.samePixels(raster) } ?: raster
        return PageSnapshot(
            grid,
            stable,
            if (full) grid.side * grid.side else count * PATCH * PATCH,
        )
    }

    companion object {
        private const val PATCH = 8
    }
}
