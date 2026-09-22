package io.github.fopwoc.mods.palimpsest.tree

/** Reads fixed-width fields MSB-first from a [ByteSource]; [finish] drops the padding bits. */
class BitReader(private val source: ByteSource) {
    private var accumulator = 0L
    private var available = 0

    fun read(bits: Int): Int {
        require(bits in 0..32)
        if (bits == 0) return 0
        while (available < bits) {
            accumulator = (accumulator shl 8) or source.byte().toLong()
            available += 8
        }
        available -= bits
        return ((accumulator ushr available) and ((1L shl bits) - 1)).toInt()
    }

    fun finish() {
        accumulator = 0
        available = 0
    }
}
