package io.github.fopwoc.mods.palimpsest.tree

/**
 * Frequency counts over a small alphabet that adapt as symbols are coded, identical on both
 * sides, so a record needs no table. Counts start at one; when the total reaches [LIMIT] every
 * count halves, which keeps the coder's precision and lets the model follow local statistics.
 */
class AdaptiveModel(val alphabet: Int, private val increment: Int = 24) {
    private val counts = IntArray(alphabet) { 1 }
    private var total = alphabet

    init {
        require(alphabet in 1..MAX_ALPHABET)
    }

    fun encode(encoder: RangeEncoder, symbol: Int) {
        var cumulative = 0
        for (index in 0 until symbol) cumulative += counts[index]
        encoder.encode(cumulative, counts[symbol], total)
        update(symbol)
    }

    fun decode(decoder: RangeDecoder): Int {
        val target = decoder.peek(total)
        var symbol = 0
        var cumulative = 0
        while (cumulative + counts[symbol] <= target) {
            cumulative += counts[symbol]
            symbol++
        }
        decoder.consume(cumulative, counts[symbol], total)
        update(symbol)
        return symbol
    }

    private fun update(symbol: Int) {
        counts[symbol] += increment
        total += increment
        if (total >= LIMIT) {
            total = 0
            for (index in counts.indices) {
                counts[index] = (counts[index] + 1) ushr 1
                total += counts[index]
            }
        }
    }

    companion object {
        const val MAX_ALPHABET = 256
        private const val LIMIT = 1 shl 15
    }
}
