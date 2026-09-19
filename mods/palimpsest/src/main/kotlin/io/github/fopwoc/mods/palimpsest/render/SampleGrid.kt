package io.github.fopwoc.mods.palimpsest.render

import io.github.fopwoc.mods.palimpsest.tree.Sample

/**
 * The facts under one page's pixels plus a one-pixel border to the north and west, so slope
 * shading at the page edge sees its neighbours. Cell (-1, -1) is the first array element; a
 * block of [NONE] means the map has nothing there.
 */
class SampleGrid(val side: Int) {
    val stride = side + 1
    val block = IntArray(stride * stride) { NONE }
    val height = IntArray(stride * stride)
    val depth = IntArray(stride * stride)
    val biome = IntArray(stride * stride)

    fun index(x: Int, z: Int): Int = (z + 1) * stride + (x + 1)

    fun set(x: Int, z: Int, sample: Sample) {
        if (sample.isNone) return
        val at = index(x, z)
        block[at] = sample.block
        height[at] = sample.height
        depth[at] = sample.depth
        biome[at] = sample.biome
    }

    fun set(x: Int, z: Int, block: Int, height: Int, depth: Int, biome: Int) {
        val at = index(x, z)
        this.block[at] = block
        this.height[at] = height
        this.depth[at] = depth
        this.biome[at] = biome
    }

    fun isPresent(x: Int, z: Int): Boolean = block[index(x, z)] > 0

    companion object {
        /** Not observed; distinct from block 0, which is observed and see-through. */
        const val NONE = -1
    }
}
