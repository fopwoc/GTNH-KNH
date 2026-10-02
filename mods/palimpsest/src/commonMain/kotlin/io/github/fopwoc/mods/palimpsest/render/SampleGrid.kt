package io.github.fopwoc.mods.palimpsest.render

import io.github.fopwoc.mods.palimpsest.tree.Sample

/** One coherent packed sample per cell, including the north/west relief halo. */
class SampleGrid private constructor(val side: Int, internal val samples: LongArray) {
    constructor(side: Int) : this(side, LongArray((side + 1) * (side + 1)) { Sample.NONE.packed })

    init {
        require(side > 0)
    }

    val stride = side + 1
    internal val bytes: Long
        get() = samples.size.toLong() * Long.SIZE_BYTES

    internal fun copy(): SampleGrid = SampleGrid(side, samples.copyOf())

    fun index(x: Int, z: Int): Int = (z + 1) * stride + (x + 1)

    fun sample(at: Int): Sample = Sample(samples[at])

    fun set(x: Int, z: Int, sample: Sample) {
        samples[index(x, z)] = sample.packed
    }

    fun set(x: Int, z: Int, block: Int, height: Int, depth: Int, biome: Int) {
        if (block == NONE) {
            set(x, z, Sample.NONE)
            return
        }
        require(block in 0..65535 && height in 0..255 && depth in 0..255 && biome in 0..65535)
        set(x, z, Sample(block, height, depth, biome))
    }

    fun isPresent(x: Int, z: Int): Boolean = sample(index(x, z)).let { !it.isNone && it.block > 0 }

    companion object {
        /** Not observed; distinct from block 0, which is observed and see-through. */
        const val NONE = -1
    }
}
