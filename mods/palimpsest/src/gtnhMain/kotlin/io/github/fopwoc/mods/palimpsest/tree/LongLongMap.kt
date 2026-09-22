package io.github.fopwoc.mods.palimpsest.tree

/**
 * Open-addressing map from long to long without boxing, for the content index of a million tiles in
 * a few tens of MB. Keys are never removed; [EMPTY_KEY] is reserved.
 */
class LongLongMap(initialCapacity: Int = 1024) {
    private var keys = LongArray(Integer.highestOneBit(maxOf(initialCapacity, 16) * 2 - 1))
    private var values = LongArray(keys.size)
    var size = 0
        private set

    init {
        keys.fill(EMPTY_KEY)
    }

    fun get(key: Long): Long? {
        require(key != EMPTY_KEY)
        var index = slot(key, keys.size)
        while (true) {
            val found = keys[index]
            if (found == key) return values[index]
            if (found == EMPTY_KEY) return null
            index = (index + 1) and (keys.size - 1)
        }
    }

    fun put(key: Long, value: Long) {
        require(key != EMPTY_KEY)
        if ((size + 1) * 4 > keys.size * 3) grow()
        var index = slot(key, keys.size)
        while (true) {
            val found = keys[index]
            if (found == key) {
                values[index] = value
                return
            }
            if (found == EMPTY_KEY) {
                keys[index] = key
                values[index] = value
                size++
                return
            }
            index = (index + 1) and (keys.size - 1)
        }
    }

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        keys = LongArray(oldKeys.size * 2).also { it.fill(EMPTY_KEY) }
        values = LongArray(keys.size)
        size = 0
        for (index in oldKeys.indices) if (oldKeys[index] != EMPTY_KEY)
            put(oldKeys[index], oldValues[index])
    }

    private fun slot(key: Long, capacity: Int): Int =
        ((key * MIX) ushr 32).toInt() and (capacity - 1)

    companion object {
        /** Hashes are mixed before use, so a zero content hash is remapped rather than reserved. */
        const val EMPTY_KEY = Long.MIN_VALUE
        private const val MIX = -0x61c8864680b583ebL
    }
}
