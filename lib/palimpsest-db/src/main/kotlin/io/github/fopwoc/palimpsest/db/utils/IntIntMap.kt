package io.github.fopwoc.palimpsest.db.utils

/**
 * Open-addressing map from int to int without boxing, for palettes built thousands of times a
 * commit. Keys are never removed; [EMPTY_KEY] is reserved.
 */
internal class IntIntMap(expected: Int = 16) {
    private var keys =
        IntArray(Integer.highestOneBit(maxOf(expected, 8) * 2 - 1) * 2).also { it.fill(EMPTY_KEY) }
    private var values = IntArray(keys.size)

    var size = 0
        private set

    /** The value for [key], or [missing]. */
    fun get(key: Int, missing: Int = -1): Int {
        var at = slot(key)
        while (true) {
            val found = keys[at]
            if (found == key) return values[at]
            if (found == EMPTY_KEY) return missing
            at = (at + 1) and (keys.size - 1)
        }
    }

    /** The value for [key], adding [next] for it first when it is new. */
    inline fun getOrPut(key: Int, next: () -> Int): Int {
        val found = get(key, MISSING)
        if (found != MISSING) return found
        return next().also { put(key, it) }
    }

    fun put(key: Int, value: Int) {
        require(key != EMPTY_KEY)
        if ((size + 1) * 2 > keys.size) grow()
        var at = slot(key)
        while (true) {
            val found = keys[at]
            if (found == key || found == EMPTY_KEY) {
                if (found == EMPTY_KEY) size++
                keys[at] = key
                values[at] = value
                return
            }
            at = (at + 1) and (keys.size - 1)
        }
    }

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        keys = IntArray(oldKeys.size * 2).also { it.fill(EMPTY_KEY) }
        values = IntArray(keys.size)
        size = 0
        for (at in oldKeys.indices) if (oldKeys[at] != EMPTY_KEY) put(oldKeys[at], oldValues[at])
    }

    private fun slot(key: Int): Int = ((key * MIX) ushr 16) and (keys.size - 1)

    companion object {
        const val EMPTY_KEY = Int.MIN_VALUE
        const val MISSING = Int.MIN_VALUE
        private const val MIX = -0x61c88647
    }
}
