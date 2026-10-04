package io.github.fopwoc.mods.palimpsest.prototype.volume.storage.tree

/** Bounded LRU of decoded records by pack ref; values are never mutated after insertion. */
class RefCache<T : Any>(private val capacity: Int) {
    private val entries =
        object : LinkedHashMap<Long, T>(1024, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, T>): Boolean =
                size > capacity
        }

    var misses = 0L
        private set

    fun get(ref: Long, load: () -> T): T {
        synchronized(entries) { entries[ref] }?.let { return it }
        val value = load()
        synchronized(entries) {
            misses++
            entries[ref] = value
        }
        return value
    }

    fun put(ref: Long, value: T) = synchronized(entries) { entries[ref] = value }

    fun clear() = synchronized(entries) { entries.clear() }
}
