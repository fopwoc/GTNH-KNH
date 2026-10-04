package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.codec.ContentHash
import io.github.fopwoc.palimpsest.db.store.BlobRef

/**
 * Recently stored content by hash, so common sections (solid stone, open sky over water) are stored
 * once without a table of everything ever written. A miss only costs a duplicate blob.
 */
internal class DedupCache(private val capacity: Int = 1 shl 16) {
    private val blobs =
        object : LinkedHashMap<ContentHash, BlobRef>(1024, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ContentHash, BlobRef>) =
                size > capacity
        }

    @Synchronized fun get(hash: ContentHash): BlobRef? = blobs[hash]

    /** The blob already known for its hash, or [blob] once it is remembered. */
    @Synchronized fun remember(blob: BlobRef): BlobRef = blobs.putIfAbsent(blob.hash, blob) ?: blob
}
