package io.github.fopwoc.palimpsest.db.utils

/** A thread-safe map keeping the [capacity] most recently used entries. */
internal class LruCache<K, V : Any>(private val capacity: Int) {
    private val entries =
        object : LinkedHashMap<K, V>(minOf(capacity, 1024), 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>) = size > capacity
        }

    @Synchronized fun get(key: K): V? = entries[key]

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = value
    }

    /** The value already there for [key], or [value] once it is put. */
    @Synchronized fun putIfAbsent(key: K, value: V): V = entries.putIfAbsent(key, value) ?: value

    @Synchronized fun clear() = entries.clear()
}
