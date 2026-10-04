package io.github.fopwoc.palimpsest.db.utils

/**
 * A thread-safe map keeping about the [capacity] most recently used entries, split into [stripes]
 * by key hash so threads working on different keys rarely wait for each other. Each stripe evicts
 * on its own, so recency is approximate across stripes.
 */
internal class LruCache<K : Any, V : Any>(capacity: Int, private val stripes: Int = 16) {
    private val parts =
        Array(stripes) {
            val limit = maxOf(1, capacity / stripes)
            object : LinkedHashMap<K, V>(minOf(limit, 1024), 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>) = size > limit
            }
        }

    fun get(key: K): V? = part(key).let { synchronized(it) { it[key] } }

    fun put(key: K, value: V) {
        part(key).let { synchronized(it) { it[key] = value } }
    }

    /** The value already there for [key], or [value] once it is put. */
    fun putIfAbsent(key: K, value: V): V =
        part(key).let { synchronized(it) { it.putIfAbsent(key, value) ?: value } }

    fun clear() = parts.forEach { synchronized(it) { it.clear() } }

    private fun part(key: K): LinkedHashMap<K, V> {
        val hash = key.hashCode()
        return parts[((hash xor (hash ushr 16)) and Int.MAX_VALUE) % stripes]
    }
}
