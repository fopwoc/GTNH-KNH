package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.utils.IntIntMap

/**
 * One 16³ section the way Minecraft holds it: a [palette] of [BlockId] raws and [bits]-wide indices
 * into it packed into [data], 64 / bits per long, never spanning two longs, in YZX order (x
 * fastest, then z, then y). A single-entry palette needs no indices: [bits] is 0 and [data] empty.
 * The palette may hold entries no block uses. The arrays are taken as they are; don't change them
 * later.
 */
class SectionBlocks(val palette: IntArray, val bits: Int, val data: LongArray) {
    init {
        require(bits in 0..MAX_BITS) { "Index width out of range: $bits" }
        if (bits == 0)
            require(palette.size == 1 && data.isEmpty()) { "A 0-bit section has one block" }
        else {
            require(palette.size in 1..(1 shl bits)) { "Palette of ${palette.size} for $bits bits" }
            require(data.size == longsFor(bits)) {
                "Expected ${longsFor(bits)} longs, got ${data.size}"
            }
        }
    }

    /** Every block as a [BlockId] raw, in YZX order. */
    fun unpack(): IntArray {
        if (bits == 0) return IntArray(VOLUME) { palette[0] }
        val out = IntArray(VOLUME)
        val perLong = Long.SIZE_BITS / bits
        val mask = (1L shl bits) - 1
        var at = 0
        for (word in data) {
            var rest = word
            var left = minOf(perLong, VOLUME - at)
            while (left-- > 0) {
                out[at++] = palette[(rest and mask).toInt()]
                rest = rest ushr bits
            }
        }
        return out
    }

    /**
     * 64 bits of the packed form, never 0. Equal packed forms mean equal blocks, so an unchanged
     * section is recognized without unpacking it; different packings of equal blocks only cost the
     * slow path.
     */
    internal fun fingerprint(): Long {
        var a = FINGERPRINT_SEED + bits
        var b = FINGERPRINT_SEED xor palette.size.toLong()
        for (id in palette) a = mix(a, id.toLong())
        var at = 0
        while (at + 1 < data.size) {
            a = mix(a, data[at])
            b = mix(b, data[at + 1])
            at += 2
        }
        if (at < data.size) a = mix(a, data[at])
        return mix(a, b).takeIf { it != 0L } ?: 1L
    }

    companion object {
        const val SIDE = 16
        const val VOLUME = SIDE * SIDE * SIDE
        private const val MAX_BITS = 16

        /** Index of block (x, y, z) inside a section, all three in 0..15. */
        fun index(x: Int, y: Int, z: Int): Int = (y shl 8) or (z shl 4) or x

        /**
         * Packs [blocks] ([BlockId] raws in YZX order) with the smallest palette, in order of first
         * appearance, so equal blocks always pack the same.
         */
        fun of(blocks: IntArray): SectionBlocks {
            require(blocks.size == VOLUME) { "A section has $VOLUME blocks, got ${blocks.size}" }
            val slots = IntIntMap()
            val indices = IntArray(VOLUME)
            var paletteSize = 0
            val palette = IntArray(VOLUME)
            for (at in 0 until VOLUME) {
                val block = blocks[at]
                indices[at] =
                    slots.getOrPut(block) { paletteSize.also { palette[paletteSize++] = block } }
            }
            if (paletteSize == 1) return SectionBlocks(intArrayOf(blocks[0]), 0, LongArray(0))
            val bits = Int.SIZE_BITS - Integer.numberOfLeadingZeros(paletteSize - 1)
            val perLong = Long.SIZE_BITS / bits
            val data = LongArray(longsFor(bits))
            var at = 0
            for (word in data.indices) {
                var packed = 0L
                var shift = 0
                var left = minOf(perLong, VOLUME - at)
                while (left-- > 0) {
                    packed = packed or (indices[at++].toLong() shl shift)
                    shift += bits
                }
                data[word] = packed
            }
            return SectionBlocks(palette.copyOf(paletteSize), bits, data)
        }

        private const val FINGERPRINT_SEED = 0x51A7E5B10C4D7E21L

        private fun mix(hash: Long, value: Long): Long {
            val mixed = (hash xor value) * -0x40a7b892e31b1a47L
            return mixed xor (mixed ushr 31)
        }

        private fun longsFor(bits: Int): Int {
            val perLong = Long.SIZE_BITS / bits
            return (VOLUME + perLong - 1) / perLong
        }
    }
}
