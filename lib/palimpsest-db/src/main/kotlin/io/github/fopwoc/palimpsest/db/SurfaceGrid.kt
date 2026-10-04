package io.github.fopwoc.palimpsest.db

/**
 * A window of the world seen from above at one moment, per block column: the first block the map
 * does not look through, its Y, the water depth above it and the biome. Columns are addressed in
 * world block coordinates; chunks never seen by then are not [present] and read as air at Y 0.
 */
class SurfaceGrid
internal constructor(
    val window: ChunkWindow,
    private val blocks: IntArray,
    private val heights: IntArray,
    private val depths: IntArray,
    private val biomes: IntArray,
    private val present: BooleanArray,
) {
    fun present(chunk: ChunkPos): Boolean =
        chunk in window && present[(chunk.z - window.z0) * window.width + chunk.x - window.x0]

    fun block(x: Int, z: Int): BlockId = BlockId(blocks[column(x, z)])

    fun height(x: Int, z: Int): Int = heights[column(x, z)]

    fun depth(x: Int, z: Int): Int = depths[column(x, z)]

    fun biome(x: Int, z: Int): BiomeId = BiomeId(biomes[column(x, z)])

    private fun column(x: Int, z: Int): Int {
        val localX = x - window.x0 * 16
        val localZ = z - window.z0 * 16
        require(localX in 0 until window.width * 16 && localZ in 0 until window.height * 16) {
            "Column $x, $z is outside $window"
        }
        return localZ * window.width * 16 + localX
    }

    internal companion object {
        /** An empty grid for [window], filled chunk by chunk with [put]. */
        fun builder(window: ChunkWindow): Builder = Builder(window)
    }

    internal class Builder(private val window: ChunkWindow) {
        private val columns = window.width * 16 * window.height * 16
        private val blocks = IntArray(columns)
        private val heights = IntArray(columns)
        private val depths = IntArray(columns)
        private val biomes = IntArray(columns)
        private val present = BooleanArray(window.width * window.height)

        /** Copies a chunk's 16×16 columns in; distinct chunks may be put from distinct threads. */
        fun put(
            chunk: ChunkPos,
            block: IntArray,
            height: IntArray,
            depth: IntArray,
            biome: IntArray,
        ) {
            val stride = window.width * 16
            val base = (chunk.z - window.z0) * 16 * stride + (chunk.x - window.x0) * 16
            for (z in 0 until 16) {
                val row = base + z * stride
                System.arraycopy(block, z * 16, blocks, row, 16)
                System.arraycopy(height, z * 16, heights, row, 16)
                System.arraycopy(depth, z * 16, depths, row, 16)
                System.arraycopy(biome, z * 16, biomes, row, 16)
            }
            present[(chunk.z - window.z0) * window.width + chunk.x - window.x0] = true
        }

        fun build() = SurfaceGrid(window, blocks, heights, depths, biomes, present)
    }
}
