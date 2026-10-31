package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneFace
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import io.github.fopwoc.mods.palimpsest.render.TerrainShader

/**
 * One chunk of blocks as quads, the same for every camera. Only faces that meet an empty block are
 * kept, and in each slice of the chunk the faces of one colour merge into the largest rectangles
 * they fill. Faces turned down are left out: the map is always seen from above. Faces are unlit,
 * the scene's light falls on them by the way they face; tops carry a slope relief from the
 * north-west like the columns do. Water shows only where it meets the open, coloured as the map
 * colours water over its floor, and shines with the blocks the palette calls shiny; a decoration
 * lies as a thin decal on what holds it. Blocks above [ceiling] are cut away.
 */
object VoxelMesher {
    fun mesh(chunk: VoxelChunk, palette: VoxelPalette, ceiling: Int? = null): GpuSceneMesh {
        val height =
            ceiling?.let { (it + 1 - chunk.minY).coerceIn(0, chunk.height) } ?: chunk.height
        val kinds = Array(SIDE * SIDE * height) { at -> palette.kind(chunk.blocks[at]) }
        val cells = Cells(chunk, kinds, height)
        val out = PackedQuads()
        val mask = IntArray(SIDE * maxOf(SIDE, height))
        for (face in Face.entries) {
            val (slices, across, along) = face.extent(height)
            for (slice in 0 until slices) {
                for (b in 0 until along) for (a in 0 until across) {
                    mask[b * across + a] = cells.faceColor(face, slice, a, b, palette)
                }
                merge(mask, across, along) { color, a, b, width, length ->
                    face.emit(out, color, slice, a, b, width, length, chunk.minY)
                }
            }
        }
        return out.build(chunk.x * SIDE.toDouble(), chunk.z * SIDE.toDouble(), 1)
    }

    /** Greedy rectangles of equal non-zero colours over an [across] × [along] mask, cleared. */
    private inline fun merge(
        mask: IntArray,
        across: Int,
        along: Int,
        emit: (color: Int, a: Int, b: Int, width: Int, length: Int) -> Unit,
    ) {
        for (b in 0 until along) {
            var a = 0
            while (a < across) {
                val color = mask[b * across + a]
                if (color == 0) {
                    a++
                    continue
                }
                var width = 1
                while (a + width < across && mask[b * across + a + width] == color) width++
                var length = 1
                grow@ while (b + length < along) {
                    for (k in 0 until width) if (mask[(b + length) * across + a + k] != color)
                        break@grow
                    length++
                }
                for (row in b until b + length) mask.fill(
                    0,
                    row * across + a,
                    row * across + a + width,
                )
                emit(color, a, b, width, length)
                a += width
            }
        }
    }

    /** The chunk's blocks with their kinds, cut at [height]. */
    private class Cells(
        private val chunk: VoxelChunk,
        private val kinds: Array<VoxelKind>,
        private val height: Int,
    ) {
        fun kind(x: Int, y: Int, z: Int): VoxelKind =
            if (x !in 0 until SIDE || z !in 0 until SIDE || y !in 0 until height) VoxelKind.EMPTY
            else kinds[(y * SIDE + z) * SIDE + x]

        private fun open(x: Int, y: Int, z: Int) =
            kind(x, y, z).let {
                it == VoxelKind.EMPTY || it == VoxelKind.DECAL
            }

        /** The colour of [face] of the block at slice, a, b; 0 where no face shows. */
        fun faceColor(face: Face, slice: Int, a: Int, b: Int, palette: VoxelPalette): Int {
            val (x, y, z) = face.block(slice, a, b)
            val kind = kind(x, y, z)
            if (face == Face.DECAL) {
                if (kind != VoxelKind.DECAL) return 0
                val color = palette.color(chunk.at(x, y, z), biome(x, z))
                return if (color == 0) 0 else GpuSceneMesh.color(color, GpuSceneFace.UP)
            }
            if (kind != VoxelKind.SOLID && kind != VoxelKind.WATER) return 0
            if (!open(x + face.dx, y + face.dy, z + face.dz)) return 0
            val raw = chunk.at(x, y, z)
            val biome = biome(x, z)
            val color =
                if (kind == VoxelKind.WATER) waterColor(x, y, z, raw, biome, palette)
                else palette.color(raw, biome)
            if (color == 0) return 0
            val relief =
                if (face == Face.UP)
                    TerrainShader.shade(
                        color,
                        TerrainShader.hillshade(rise(x - 1, y, z), rise(x, y, z - 1), 0),
                    )
                else color
            val shiny = kind == VoxelKind.WATER || palette.isShiny(raw)
            return GpuSceneMesh.color(relief, face.scene, shiny)
        }

        /** How a top at [y] stands against the column beside it: -1 under, 0 level, 1 above. */
        private fun rise(x: Int, y: Int, z: Int): Int =
            when {
                x !in 0 until SIDE || z !in 0 until SIDE -> 0
                !open(x, y + 1, z) -> -1
                open(x, y, z) -> 1
                else -> 0
            }

        private fun waterColor(
            x: Int,
            y: Int,
            z: Int,
            raw: Int,
            biome: Int,
            palette: VoxelPalette,
        ): Int {
            var below = y
            while (below >= 0 && kind(x, below, z) == VoxelKind.WATER) below--
            val floor = if (below >= 0) chunk.at(x, below, z) else 0
            return palette.water(floor, raw, y - below, biome)
        }

        private fun biome(x: Int, z: Int): Int = chunk.biomes[z * SIDE + x]
    }

    /**
     * A face direction: which way it points, how its slices and rectangles map onto blocks, and how
     * a rectangle becomes a quad. [DECAL] is the flat top of decorations.
     */
    private enum class Face(val dx: Int, val dy: Int, val dz: Int, val scene: GpuSceneFace) {
        UP(0, 1, 0, GpuSceneFace.UP),
        EAST(1, 0, 0, GpuSceneFace.EAST),
        WEST(-1, 0, 0, GpuSceneFace.WEST),
        SOUTH(0, 0, 1, GpuSceneFace.SOUTH),
        NORTH(0, 0, -1, GpuSceneFace.NORTH),
        DECAL(0, 1, 0, GpuSceneFace.UP);

        /** Slices, then cells across and along each slice. */
        fun extent(height: Int): Triple<Int, Int, Int> =
            when (this) {
                UP,
                DECAL -> Triple(height, SIDE, SIDE)
                EAST,
                WEST -> Triple(SIDE, SIDE, height)
                SOUTH,
                NORTH -> Triple(SIDE, SIDE, height)
            }

        /** The block whose face cell (a, b) of [slice] is. */
        fun block(slice: Int, a: Int, b: Int): Triple<Int, Int, Int> =
            when (this) {
                UP,
                DECAL -> Triple(a, slice, b)
                EAST,
                WEST -> Triple(slice, b, a)
                SOUTH,
                NORTH -> Triple(a, b, slice)
            }

        @Suppress("LongParameterList")
        fun emit(
            out: PackedQuads,
            color: Int,
            slice: Int,
            a: Int,
            b: Int,
            width: Int,
            length: Int,
            minY: Int,
        ) {
            val s = SUB
            when (this) {
                UP,
                DECAL -> {
                    val y = (minY + slice + 1) * s + if (this == DECAL) DECAL_SIXTEENTHS - s else 0
                    out.quad(
                        color,
                        a,
                        y,
                        b,
                        a + width,
                        y,
                        b,
                        a + width,
                        y,
                        b + length,
                        a,
                        y,
                        b + length,
                    )
                }
                EAST,
                WEST -> {
                    val x = if (this == EAST) slice + 1 else slice
                    val y0 = (minY + b) * s
                    val y1 = (minY + b + length) * s
                    out.quad(color, x, y0, a, x, y0, a + width, x, y1, a + width, x, y1, a)
                }
                SOUTH,
                NORTH -> {
                    val z = if (this == SOUTH) slice + 1 else slice
                    val y0 = (minY + b) * s
                    val y1 = (minY + b + length) * s
                    out.quad(color, a, y0, z, a + width, y0, z, a + width, y1, z, a, y1, z)
                }
            }
        }
    }

    private const val SIDE = VoxelChunk.SIDE
    private const val SUB = GpuSceneMesh.SUBDIVISIONS
    /** How far above the ground a decoration's decal lies, in sixteenths. */
    private const val DECAL_SIXTEENTHS = 1
}
