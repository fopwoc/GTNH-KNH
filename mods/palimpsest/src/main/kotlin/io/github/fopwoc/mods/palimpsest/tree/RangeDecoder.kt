package io.github.fopwoc.mods.palimpsest.tree

/** Mirror of [RangeEncoder]; reads exactly the bytes the encoder emitted plus its 5-byte tail. */
class RangeDecoder(private val source: ByteSource) {
    private var code = 0
    private var range = -1

    init {
        repeat(5) { code = (code shl 8) or source.byte() }
    }

    /** The cumulative frequency the next symbol falls in; follow with [consume]. */
    fun peek(total: Int): Int {
        val step = Integer.divideUnsigned(range, total)
        val found = Integer.divideUnsigned(code, step)
        return if (Integer.compareUnsigned(found, total) < 0) found else total - 1
    }

    fun consume(cumulative: Int, frequency: Int, total: Int) {
        val step = Integer.divideUnsigned(range, total)
        code -= step * cumulative
        range = step * frequency
        while (Integer.compareUnsigned(range, RangeEncoder.TOP) < 0) {
            range = range shl 8
            code = (code shl 8) or source.byte()
        }
    }

    fun decodeBits(bits: Int): Int {
        var value = 0
        repeat(bits) {
            range = range ushr 1
            val one = Integer.compareUnsigned(code, range) >= 0
            if (one) code -= range
            value = (value shl 1) or (if (one) 1 else 0)
            while (Integer.compareUnsigned(range, RangeEncoder.TOP) < 0) {
                range = range shl 8
                code = (code shl 8) or source.byte()
            }
        }
        return value
    }
}
