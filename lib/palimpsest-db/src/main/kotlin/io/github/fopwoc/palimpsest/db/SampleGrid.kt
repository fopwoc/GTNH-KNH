package io.github.fopwoc.palimpsest.db

/**
 * The world at far zoom: one sample per cell of 2^[level] × 2^[level] chunks, the center column of
 * the first chunk in Z-order that existed at the moment, never an average. Cells are addressed in
 * cell coordinates: cell (x, z) covers chunks x · 2^level until (x + 1) · 2^level.
 */
class SampleGrid
internal constructor(
    val level: Int,
    val x0: Int,
    val z0: Int,
    val width: Int,
    val height: Int,
    private val samples: IntArray,
    private val present: BooleanArray,
) {
    /** Chunks per cell side. */
    val cellChunks: Int
        get() = 1 shl level

    fun present(x: Int, z: Int): Boolean = present[cell(x, z)]

    fun block(x: Int, z: Int): BlockId = BlockId(samples[cell(x, z) * 4])

    fun height(x: Int, z: Int): Int = samples[cell(x, z) * 4 + 1]

    fun depth(x: Int, z: Int): Int = samples[cell(x, z) * 4 + 2]

    fun biome(x: Int, z: Int): BiomeId = BiomeId(samples[cell(x, z) * 4 + 3])

    private fun cell(x: Int, z: Int): Int {
        require(x - x0 in 0 until width && z - z0 in 0 until height) {
            "Cell $x, $z is outside the grid"
        }
        return (z - z0) * width + (x - x0)
    }
}
