package io.github.fopwoc.mods.palimpsest.tree

/** Packs fixed-width fields MSB-first into a [ByteSink]; call [finish] to flush the last byte. */
class BitWriter(private val sink: ByteSink) {
    private var accumulator = 0L
    private var filled = 0

    fun write(value: Int, bits: Int) {
        require(bits in 0..32 && (bits == 32 || value ushr bits == 0))
        if (bits == 0) return
        accumulator = (accumulator shl bits) or (value.toLong() and ((1L shl bits) - 1))
        filled += bits
        while (filled >= 8) {
            filled -= 8
            sink.byte((accumulator ushr filled).toInt() and 0xFF)
        }
    }

    fun finish() {
        if (filled > 0) sink.byte(((accumulator shl (8 - filled)) and 0xFF).toInt())
        accumulator = 0
        filled = 0
    }
}
