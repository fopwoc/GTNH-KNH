package io.github.fopwoc.mods.palimpsest.tree

/**
 * Arithmetic coder of the LZMA family: a 32-bit range, a 33-bit low with carry propagation through
 * a cache byte, symbols given as `(cumulative, frequency, total)` slices of the range. Adaptive
 * models feed it, so no table is written into the record.
 */
class RangeEncoder(private val sink: ByteSink) {
    private var low = 0L
    private var range = -1
    private var cache = 0
    private var cacheSize = 1L

    fun encode(cumulative: Int, frequency: Int, total: Int) {
        val step = Integer.divideUnsigned(range, total)
        low += step.toLong() * cumulative
        range = step * frequency
        while (Integer.compareUnsigned(range, TOP) < 0) {
            range = range shl 8
            shiftLow()
        }
    }

    /** Raw bits for escapes, coded as equiprobable symbols. */
    fun encodeBits(value: Int, bits: Int) {
        for (bit in bits - 1 downTo 0) {
            range = range ushr 1
            if ((value ushr bit) and 1 == 1) low += range.toLong() and 0xFFFFFFFFL
            while (Integer.compareUnsigned(range, TOP) < 0) {
                range = range shl 8
                shiftLow()
            }
        }
    }

    private fun shiftLow() {
        if (low < 0xFF000000L || low > 0xFFFFFFFFL) {
            val carry = (low ushr 32).toInt()
            var pending = cache
            do {
                sink.byte((pending + carry) and 0xFF)
                pending = 0xFF
            } while (--cacheSize != 0L)
            cache = ((low ushr 24) and 0xFF).toInt()
        }
        cacheSize++
        low = (low and 0x00FFFFFFL) shl 8
    }

    fun finish() {
        repeat(5) { shiftLow() }
    }

    companion object {
        const val TOP = 1 shl 24
    }
}
