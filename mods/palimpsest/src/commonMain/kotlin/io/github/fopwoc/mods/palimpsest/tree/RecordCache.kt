package io.github.fopwoc.mods.palimpsest.tree

/** Bounded LRU of decoded records by ref; immutable values, so sharing across threads is safe. */
class RecordCache<T : Any>(private val capacity: Int) {
    private val entries =
        object : LinkedHashMap<Long, T>(capacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, T>): Boolean =
                size > capacity
        }

    fun get(ref: Ref): T? = synchronized(entries) { entries[ref.packed] }

    fun put(ref: Ref, value: T) {
        synchronized(entries) { entries[ref.packed] = value }
    }

    fun getOrLoad(ref: Ref, load: (Ref) -> T): T = get(ref) ?: load(ref).also { put(ref, it) }

    fun clear() = synchronized(entries) { entries.clear() }

    val size: Int
        get() = synchronized(entries) { entries.size }
}
