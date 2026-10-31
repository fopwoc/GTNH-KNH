package io.github.fopwoc.mods.palimpsest.render.iso

/**
 * Every block of chunk ([x], [z]) from [minY] up, [height] blocks: [blocks] holds history block id
 * raws in YZX order (x fastest, then z, then y), [biomes] one biome per column, x fastest.
 */
class VoxelChunk(
    val x: Int,
    val z: Int,
    val minY: Int,
    val height: Int,
    val blocks: IntArray,
    val biomes: IntArray,
) {
    init {
        require(blocks.size == SIDE * SIDE * height)
        require(biomes.size == SIDE * SIDE)
    }

    /** The block at local [x] and [z] and [y] blocks above [minY]; 0 outside the chunk. */
    fun at(x: Int, y: Int, z: Int): Int =
        if (x !in 0 until SIDE || z !in 0 until SIDE || y !in 0 until height) 0
        else blocks[(y * SIDE + z) * SIDE + x]

    companion object {
        const val SIDE = 16
    }
}
