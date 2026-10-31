package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import io.github.fopwoc.palimpsest.db.SampleGrid
import io.github.fopwoc.palimpsest.db.SurfaceGrid

/**
 * The ground as columns: [columns] × [rows] cells of [step] × [step] blocks from block ([originX],
 * [originZ]), each with the Y its top stands at and its unlit colour, 0 where nothing was seen. The
 * top is the water's surface where there is water: [depths] blocks of it over a floor of that
 * colour, the water itself coloured [waters] before depth.
 */
class Heightfield(
    val originX: Int,
    val originZ: Int,
    val step: Int,
    val columns: Int,
    val rows: Int,
    val tops: IntArray,
    val colors: IntArray,
    val depths: IntArray = IntArray(tops.size),
    val waters: IntArray = IntArray(tops.size),
) {
    init {
        require(step > 0 && columns > 0 && rows > 0)
        require(tops.size == columns * rows && colors.size == tops.size)
        require(depths.size == tops.size && waters.size == tops.size)
    }

    fun present(column: Int, row: Int): Boolean =
        column in 0 until columns && row in 0 until rows && colors[row * columns + column] != 0

    fun top(column: Int, row: Int): Int = tops[row * columns + column]

    /** Blocks of water over the floor of a cell; 0 where it is dry. */
    fun depth(column: Int, row: Int): Int = depths[row * columns + column]

    /** The Y the floor of a cell stands at: its top, less any water over it. */
    fun floor(column: Int, row: Int): Int = top(column, row) - depth(column, row)

    /** The top under block column ([x], [z]), or null outside or where nothing was seen. */
    fun topAt(x: Int, z: Int): Int? {
        val column = Math.floorDiv(x - originX, step)
        val row = Math.floorDiv(z - originZ, step)
        return if (present(column, row)) top(column, row) else null
    }

    /** Whether this field covers every block of the given rectangle at [step]. */
    fun covers(minX: Int, minZ: Int, maxX: Int, maxZ: Int, step: Int): Boolean =
        this.step == step &&
            minX >= originX &&
            minZ >= originZ &&
            maxX <= originX + columns * step &&
            maxZ <= originZ + rows * step

    companion object {
        /** Every [step]th column of [grid], picked at the middle of its cell. */
        fun of(grid: SurfaceGrid, step: Int, drawnId: (Int) -> Int, shader: TerrainShader) =
            build(
                grid.window.x0 * CHUNK,
                grid.window.z0 * CHUNK,
                step,
                grid.window.width * CHUNK,
                grid.window.height * CHUNK,
            ) { x, z, put ->
                val depth = grid.depth(x, z)
                val biome = grid.biome(x, z).raw
                put(
                    grid.height(x, z) + 1 + depth,
                    shader.column(drawnId(grid.block(x, z).raw), biome),
                    depth,
                    if (depth > 0) shader.waterColor(biome) else 0,
                )
            }

        /**
         * Every [step]th column of [tiles], [width] × [height] tiles from tile ([x0], [z0]) row by
         * row, as the live map's own tiles see it.
         */
        @Suppress("LongParameterList")
        fun of(
            tiles: Array<TileRecord?>,
            x0: Int,
            z0: Int,
            width: Int,
            height: Int,
            step: Int,
            shader: TerrainShader,
        ): Heightfield {
            require(tiles.size == width * height)
            return build(x0 * CHUNK, z0 * CHUNK, step, width * CHUNK, height * CHUNK) { x, z, put ->
                val tile =
                    tiles[(Math.floorDiv(z, CHUNK) - z0) * width + Math.floorDiv(x, CHUNK) - x0]
                if (tile != null) {
                    val cell = Math.floorMod(z, CHUNK) * CHUNK + Math.floorMod(x, CHUNK)
                    val depth = tile.depth(cell)
                    put(
                        tile.height(cell) + 1 + depth,
                        shader.column(tile.block(cell), tile.biome(cell)),
                        depth,
                        if (depth > 0) shader.waterColor(tile.biome(cell)) else 0,
                    )
                }
            }
        }

        /** One cell per overview sample: [grid]'s cells are 2^level chunks a side. */
        fun of(grid: SampleGrid, drawnId: (Int) -> Int, shader: TerrainShader): Heightfield {
            val step = grid.cellChunks * CHUNK
            val tops = IntArray(grid.width * grid.height)
            val colors = IntArray(tops.size)
            val depths = IntArray(tops.size)
            val waters = IntArray(tops.size)
            for (row in 0 until grid.height) for (column in 0 until grid.width) {
                val x = grid.x0 + column
                val z = grid.z0 + row
                if (!grid.present(x, z)) continue
                val depth = grid.depth(x, z)
                val biome = grid.biome(x, z).raw
                val at = row * grid.width + column
                tops[at] = grid.height(x, z) + 1 + depth
                depths[at] = depth
                if (depth > 0) waters[at] = shader.waterColor(biome)
                colors[at] = shader.column(drawnId(grid.block(x, z).raw), biome)
            }
            return Heightfield(
                grid.x0 * step,
                grid.z0 * step,
                step,
                grid.width,
                grid.height,
                tops,
                colors,
                depths,
                waters,
            )
        }

        private inline fun build(
            x0: Int,
            z0: Int,
            step: Int,
            blocksWide: Int,
            blocksDeep: Int,
            sample:
                (
                    x: Int,
                    z: Int,
                    put: (top: Int, color: Int, depth: Int, water: Int) -> Unit,
                ) -> Unit,
        ): Heightfield {
            val columns = (blocksWide / step).coerceAtLeast(1)
            val rows = (blocksDeep / step).coerceAtLeast(1)
            val tops = IntArray(columns * rows)
            val colors = IntArray(tops.size)
            val depths = IntArray(tops.size)
            val waters = IntArray(tops.size)
            val middle = (step / 2).coerceAtMost(blocksWide - 1)
            for (row in 0 until rows) for (column in 0 until columns) {
                val at = row * columns + column
                sample(x0 + column * step + middle, z0 + row * step + middle) {
                    top,
                    color,
                    depth,
                    water ->
                    tops[at] = top
                    colors[at] = color
                    depths[at] = depth
                    waters[at] = water
                }
            }
            return Heightfield(x0, z0, step, columns, rows, tops, colors, depths, waters)
        }

        private const val CHUNK = 16
    }
}
