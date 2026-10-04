package io.github.fopwoc.palimpsest.db

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
        val perLong = Long.SIZE_BITS / bits
        val mask = (1L shl bits) - 1
        return IntArray(VOLUME) { at ->
            val index = (data[at / perLong] ushr ((at % perLong) * bits)) and mask
            palette[index.toInt()]
        }
    }

    companion object {
        const val SIDE = 16
        const val VOLUME = SIDE * SIDE * SIDE
        private const val MAX_BITS = 16

        /** Index of block (x, y, z) inside a section, all three in 0..15. */
        fun index(x: Int, y: Int, z: Int): Int = (y shl 8) or (z shl 4) or x

        /** Packs [blocks] ([BlockId] raws in YZX order) with the smallest palette. */
        fun of(blocks: IntArray): SectionBlocks {
            require(blocks.size == VOLUME) { "A section has $VOLUME blocks, got ${blocks.size}" }
            val slots = LinkedHashMap<Int, Int>()
            for (block in blocks) slots.getOrPut(block) { slots.size }
            if (slots.size == 1) return SectionBlocks(intArrayOf(blocks[0]), 0, LongArray(0))
            val bits = Int.SIZE_BITS - Integer.numberOfLeadingZeros(slots.size - 1)
            val perLong = Long.SIZE_BITS / bits
            val data = LongArray(longsFor(bits))
            blocks.forEachIndexed { at, block ->
                val slot = slots.getValue(block).toLong()
                data[at / perLong] = data[at / perLong] or (slot shl ((at % perLong) * bits))
            }
            return SectionBlocks(slots.keys.toIntArray(), bits, data)
        }

        private fun longsFor(bits: Int): Int {
            val perLong = Long.SIZE_BITS / bits
            return (VOLUME + perLong - 1) / perLong
        }
    }
}
