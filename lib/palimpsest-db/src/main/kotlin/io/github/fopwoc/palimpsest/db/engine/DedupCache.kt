package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.codec.ContentHash
import io.github.fopwoc.palimpsest.db.store.BlobRef
import io.github.fopwoc.palimpsest.db.utils.LruCache

/**
 * Recently stored content by hash, so common sections (solid stone, open sky over water) are stored
 * once without a table of everything ever written. A miss only costs a duplicate blob.
 */
internal class DedupCache(capacity: Int = 1 shl 16) {
    private val blobs = LruCache<ContentHash, BlobRef>(capacity)

    fun get(hash: ContentHash): BlobRef? = blobs.get(hash)

    /** The blob already known for its hash, or [blob] once it is remembered. */
    fun remember(blob: BlobRef): BlobRef = blobs.putIfAbsent(blob.hash, blob)
}
