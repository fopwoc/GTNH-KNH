package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneFace
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import kotlin.math.sqrt

/**
 * One chunk of blocks as quads, the same for every camera. Only faces that meet an empty block are
 * kept, and in each slice of the chunk the faces of one colour and shading merge into the largest
 * rectangles they fill. Faces turned down are left out: the map is always seen from above. Faces
 * are unlit, the scene's light falls on them by the way they face; tops carry a hillshade from the
 * north-west like the columns do, lean with the large shape of the ground and darken in the corners
 * walls make with them; decorations take the look of the ground they lie on. Water shows only where
 * it meets the open, and the floor under it is drawn too: the surface lies in a translucent mesh of
 * its own, as see-through as it is shallow, its depth taken at each corner so it thins towards the
 * shore, and shines with the blocks the palette calls shiny. A decoration lies as a thin decal on
 * what holds it. Blocks above [ceiling] are cut away. A face whose corners differ is drawn alone.
 */
object VoxelMesher {
    /** A chunk's blocks, and the water over them when any of it can be seen through. */
    class Meshes(val solid: GpuSceneMesh, val water: GpuSceneMesh?)

    fun mesh(chunk: VoxelChunk, palette: VoxelPalette, ceiling: Int? = null): Meshes {
        val height =
            ceiling?.let { (it + 1 - chunk.minY).coerceIn(0, chunk.height) } ?: chunk.height
        val kinds = Array(SIDE * SIDE * height) { at -> palette.kind(chunk.blocks[at]) }
        val cells = Cells(chunk, kinds, height)
        val out = PackedQuads()
        val water = PackedQuads()
        val mask = IntArray(SIDE * maxOf(SIDE, height))
        val shades = IntArray(mask.size)
        val look = Look()
        for (face in Face.entries) {
            val (slices, across, along) = face.extent(height)
            for (slice in 0 until slices) {
                for (b in 0 until along) for (a in 0 until across) {
                    val at = b * across + a
                    mask[at] = 0
                    if (!cells.look(face, slice, a, b, palette, look)) continue
                    if (look.uniform) {
                        mask[at] = look.colors[0]
                        shades[at] = look.shadings[0]
                    } else {
                        val into = if (GpuSceneMesh.opacity(look.colors[0]) < 1f) water else out
                        face.emit(into, look, slice, a, b, 1, 1, chunk.minY)
                    }
                }
                merge(mask, shades, across, along) { color, shading, a, b, width, length ->
                    val into = if (GpuSceneMesh.opacity(color) < 1f) water else out
                    look.colors.fill(color)
                    look.shadings.fill(shading)
                    face.emit(into, look, slice, a, b, width, length, chunk.minY)
                }
            }
        }
        val x = chunk.x * SIDE.toDouble()
        val z = chunk.z * SIDE.toDouble()
        return Meshes(
            out.build(x, z, 1),
            if (water.isEmpty) null else water.build(x, z, 1, translucent = true),
        )
    }

    /** A face's colour and shading at each corner, and whether they are all the same. */
    private class Look {
        val colors = IntArray(CORNERS)
        val shadings = IntArray(CORNERS)

        val uniform: Boolean
            get() = (1 until CORNERS).all { colors[it] == colors[0] && shadings[it] == shadings[0] }

        fun all(color: Int, shading: Int = GpuSceneMesh.OPEN) {
            colors.fill(color)
            shadings.fill(shading)
        }
    }

    /**
     * Greedy rectangles of equal non-zero colours with equal [shades] over an [across] × [along]
     * mask, cleared.
     */
    private inline fun merge(
        mask: IntArray,
        shades: IntArray,
        across: Int,
        along: Int,
        emit: (color: Int, shading: Int, a: Int, b: Int, width: Int, length: Int) -> Unit,
    ) {
        for (b in 0 until along) {
            var a = 0
            while (a < across) {
                val color = mask[b * across + a]
                if (color == 0) {
                    a++
                    continue
                }
                val shading = shades[b * across + a]
                var width = 1
                while (
                    a + width < across &&
                        mask[b * across + a + width] == color &&
                        shades[b * across + a + width] == shading
                ) width++
                var length = 1
                grow@ while (b + length < along) {
                    for (k in 0 until width) {
                        val at = (b + length) * across + a + k
                        if (mask[at] != color || shades[at] != shading) break@grow
                    }
                    length++
                }
                for (row in b until b + length) mask.fill(
                    0,
                    row * across + a,
                    row * across + a + width,
                )
                emit(color, shading, a, b, width, length)
                a += width
            }
        }
    }

    /**
     * The chunk's blocks with their kinds, cut at [height], and the large shape of its ground: the
     * top of each column's highest block through [GroundSmoothing], leaned at every corner between
     * columns. Past the chunk's edge, whose blocks are not here, the ground carries on as it is at
     * the edge.
     */
    private class Cells(
        private val chunk: VoxelChunk,
        private val kinds: Array<VoxelKind>,
        private val height: Int,
    ) {
        private val leanX = FloatArray(CORNER_SIDE * CORNER_SIDE)
        private val leanZ = FloatArray(leanX.size)

        init {
            val ground =
                FloatArray(SIDE * SIDE) { at ->
                    val x = at % SIDE
                    val z = at / SIDE
                    var y = height - 1
                    while (y >= 0 && kinds[(y * SIDE + z) * SIDE + x] != VoxelKind.SOLID) y--
                    if (y < 0) Float.NaN else y + 1f
                }
            GroundSmoothing.smooth(ground, SIDE, SIDE, FEATURE_RADIUS, BLUR_RADIUS)
            fun h(x: Int, z: Int) = ground[z.coerceIn(0, SIDE - 1) * SIDE + x.coerceIn(0, SIDE - 1)]
            for (z in 0..SIDE) for (x in 0..SIDE) {
                // The slope across the four columns meeting at the corner, from whichever pairs
                // were seen.
                fun along(a: Float, b: Float) = if (a.isNaN() || b.isNaN()) Float.NaN else b - a
                val gx = mean(along(h(x - 1, z - 1), h(x, z - 1)), along(h(x - 1, z), h(x, z)))
                val gz = mean(along(h(x - 1, z - 1), h(x - 1, z)), along(h(x, z - 1), h(x, z)))
                val length = sqrt(gx * gx + gz * gz + 1)
                leanX[z * CORNER_SIDE + x] = -gx / length
                leanZ[z * CORNER_SIDE + x] = -gz / length
            }
        }

        private fun mean(a: Float, b: Float): Float =
            when {
                a.isNaN() && b.isNaN() -> 0f
                a.isNaN() -> b
                b.isNaN() -> a
                else -> (a + b) / 2
            }

        fun kind(x: Int, y: Int, z: Int): VoxelKind =
            if (x !in 0 until SIDE || z !in 0 until SIDE || y !in 0 until height) VoxelKind.EMPTY
            else kinds[(y * SIDE + z) * SIDE + x]

        private fun open(x: Int, y: Int, z: Int) =
            kind(x, y, z).let {
                it == VoxelKind.EMPTY || it == VoxelKind.DECAL
            }

        /** Whether a block's face towards here can be seen: through air, or through water. */
        private fun seen(x: Int, y: Int, z: Int) = open(x, y, z) || kind(x, y, z) == VoxelKind.WATER

        private fun solid(x: Int, y: Int, z: Int) = kind(x, y, z) == VoxelKind.SOLID

        /** Whether water lies over the block, past whatever solid blocks are on it. */
        private fun underWater(x: Int, y: Int, z: Int): Boolean {
            var above = y + 1
            while (solid(x, above, z)) above++
            return kind(x, above, z) == VoxelKind.WATER
        }

        /**
         * Fills [look] with [face] of the block at slice, a, b: corners in the order [Face.emit]
         * lays them; false where no face shows.
         *
         * A wall facing out of the chunk does not know what stands beyond it. In the open the next
         * chunk hides it where it should not show; under water nothing would, so there water and
         * the ground under it are taken to carry on, and no wall is drawn.
         */
        @Suppress("ReturnCount")
        fun look(
            face: Face,
            slice: Int,
            a: Int,
            b: Int,
            palette: VoxelPalette,
            look: Look,
        ): Boolean {
            val (x, y, z) = face.block(slice, a, b)
            val kind = kind(x, y, z)
            if (face == Face.DECAL) {
                if (kind != VoxelKind.DECAL) return false
                val color = palette.color(chunk.at(x, y, z), biome(x, z))
                if (color == 0) return false
                // Snow, flowers, rails: shaded as the ground they lie on.
                top(x, y - 1, z, color, shiny = false, look)
                return true
            }
            val neighbour = Triple(x + face.dx, y + face.dy, z + face.dz)
            val outward = neighbour.first !in 0 until SIDE || neighbour.third !in 0 until SIDE
            when (kind) {
                VoxelKind.WATER -> {
                    if (outward) return false
                    if (!open(neighbour.first, neighbour.second, neighbour.third)) return false
                    water(x, y, z, face, palette, look)
                    return true
                }
                VoxelKind.SOLID -> {
                    if (!seen(neighbour.first, neighbour.second, neighbour.third)) return false
                    if (outward && underWater(x, y, z)) return false
                }
                else -> return false
            }
            val raw = chunk.at(x, y, z)
            val color = palette.color(raw, biome(x, z))
            if (color == 0) return false
            if (face != Face.UP) {
                look.colors.fill(GpuSceneMesh.color(color, face.scene, palette.isShiny(raw)))
                look.shadings.fill(GpuSceneMesh.OPEN)
                return true
            }
            top(x, y, z, color, palette.isShiny(raw), look)
            return true
        }

        /**
         * Fills [look] with a top at [y] of [color]: the small relief hillshaded into the colour,
         * as the columns and the flat map have it, the ground's large lean at each corner, and each
         * corner darkened by the walls closing it.
         */
        @Suppress("LongParameterList")
        private fun top(x: Int, y: Int, z: Int, color: Int, shiny: Boolean, look: Look) {
            val hill = TerrainShader.hillshade(-level(x - 1, y, z), -level(x, y, z - 1), 0)
            look.colors.fill(
                GpuSceneMesh.color(TerrainShader.shade(color, hill), GpuSceneFace.UP, shiny)
            )
            for (corner in 0 until CORNERS) {
                val sx = CORNER_X[corner]
                val sz = CORNER_Z[corner]
                // The classic corner shade: walls on both sides close it, each block nearer adds.
                val side = solid(x + sx, y + 1, z)
                val other = solid(x, y + 1, z + sz)
                val diagonal = solid(x + sx, y + 1, z + sz)
                val open =
                    if (side && other) 0
                    else CORNERS - 1 - listOf(side, other, diagonal).count { it }
                val at = (z + (sz + 1) / 2) * CORNER_SIDE + x + (sx + 1) / 2
                look.shadings[corner] = GpuSceneMesh.shading(leanX[at], leanZ[at], SKY[open])
            }
        }

        /**
         * Where the ground beside a top at [y] stands, in blocks up to [MAX_LEVEL] either way:
         * above while blocks rise over it, below while it is open under it. Past the chunk's edge,
         * whose blocks are not here, the ground carries on as it is at the edge.
         */
        private fun level(column: Int, y: Int, row: Int): Int {
            val x = column.coerceIn(0, SIDE - 1)
            val z = row.coerceIn(0, SIDE - 1)
            var level = 0
            if (solid(x, y + 1, z)) {
                while (level < MAX_LEVEL && solid(x, y + 1 + level, z)) level++
            } else {
                while (level > -MAX_LEVEL && !solid(x, y + level, z)) level--
            }
            return level
        }

        /**
         * Fills [look] with water's [face] at a block, as deep as the water reaches down from it,
         * nearly opaque where it reaches the chunk's bottom with no floor to see. Its surface takes
         * each corner's depth from the water blocks meeting there.
         */
        @Suppress("LongParameterList")
        private fun water(x: Int, y: Int, z: Int, face: Face, palette: VoxelPalette, look: Look) {
            val biome = biome(x, z)
            val depth = depth(x, y, z)
            look.shadings.fill(GpuSceneMesh.OPEN)
            if (face != Face.UP) {
                look.colors.fill(waterFace(palette.water(depth.toDouble(), biome), face.scene))
                return
            }
            for (corner in 0 until CORNERS) {
                var sum = 0
                var count = 0
                for ((dx, dz) in
                    listOf(0 to 0, CORNER_X[corner] to 0, 0 to CORNER_Z[corner]) +
                        (CORNER_X[corner] to CORNER_Z[corner])) {
                    if (x + dx !in 0 until SIDE || z + dz !in 0 until SIDE) continue
                    sum +=
                        if (kind(x + dx, y, z + dz) == VoxelKind.WATER) depth(x + dx, y, z + dz)
                        else 0
                    count++
                }
                val water = palette.water(sum.toDouble() / count, biome)
                look.colors[corner] = waterFace(water, GpuSceneFace.UP)
            }
        }

        /**
         * Blocks of water from [y] down to the floor; [BOTTOMLESS] where there is no floor here.
         */
        private fun depth(x: Int, y: Int, z: Int): Int {
            var below = y
            while (below >= 0 && kind(x, below, z) == VoxelKind.WATER) below--
            return if (below < 0) BOTTOMLESS else y - below
        }

        private fun waterFace(water: Int, face: GpuSceneFace) =
            GpuSceneMesh.color(
                water,
                face,
                shiny = true,
                opacity = (water ushr ALPHA_SHIFT) / OPAQUE,
            )

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

        /** Corners of an up face go round from its north-west one, as [CORNER_X] and [CORNER_Z]. */
        @Suppress("LongParameterList")
        fun emit(
            out: PackedQuads,
            look: Look,
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
                    out.corner(look.colors[0], look.shadings[0], a, y, b)
                    out.corner(look.colors[1], look.shadings[1], a + width, y, b)
                    out.corner(look.colors[2], look.shadings[2], a + width, y, b + length)
                    out.corner(look.colors[3], look.shadings[3], a, y, b + length)
                }
                EAST,
                WEST -> {
                    val x = if (this == EAST) slice + 1 else slice
                    val y0 = (minY + b) * s
                    val y1 = (minY + b + length) * s
                    out.quad(look.colors[0], x, y0, a, x, y0, a + width, x, y1, a + width, x, y1, a)
                }
                SOUTH,
                NORTH -> {
                    val z = if (this == SOUTH) slice + 1 else slice
                    val y0 = (minY + b) * s
                    val y1 = (minY + b + length) * s
                    out.quad(look.colors[0], a, y0, z, a + width, y0, z, a + width, y1, z, a, y1, z)
                }
            }
        }
    }

    private const val SIDE = VoxelChunk.SIDE
    private const val CORNERS = 4
    /** Which way each corner of an up face lies from its middle, along x and z. */
    private val CORNER_X = intArrayOf(-1, 1, 1, -1)
    private val CORNER_Z = intArrayOf(-1, -1, 1, 1)
    /** The sky a top's corner sees with none to three blocks closing it. */
    private val SKY = floatArrayOf(0.55f, 0.7f, 0.85f, 1f)
    /** Blocks a top's hillshade counts its neighbours up or down at most. */
    private const val MAX_LEVEL = 3
    private const val CORNER_SIDE = SIDE + 1
    /** Trees up to five blocks across leave the ground's shape alone. */
    private const val FEATURE_RADIUS = 2
    /** Blocks either way single steps are averaged over, as the columns do. */
    private const val BLUR_RADIUS = 4
    /** The depth of water with no floor in the chunk: deep enough to hide everything. */
    private const val BOTTOMLESS = 64
    private const val ALPHA_SHIFT = 24
    private const val OPAQUE = 255f
    private const val SUB = GpuSceneMesh.SUBDIVISIONS
    /** How far above the ground a decoration's decal lies, in sixteenths. */
    private const val DECAL_SIXTEENTHS = 1
}
