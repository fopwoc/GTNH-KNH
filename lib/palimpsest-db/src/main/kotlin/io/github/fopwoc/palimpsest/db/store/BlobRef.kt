package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.codec.ContentHash

/**
 * One stored piece of content, shared by every chunk version that holds it. A fresh blob carries
 * its encoded [pending] bytes until the writer lays it out; the writer sets [segment] and [offset]
 * before publishing the commit, and readers only reach a blob through a published commit.
 */
internal class BlobRef(val hash: ContentHash, val kind: BlobKind, val length: Int) {
    var pending: ByteArray? = null
    var segment: Int = -1
    var offset: Long = -1

    val positioned: Boolean
        get() = segment >= 0

    companion object {
        fun fresh(hash: ContentHash, kind: BlobKind, bytes: ByteArray) =
            BlobRef(hash, kind, bytes.size).apply { pending = bytes }

        fun stored(hash: ContentHash, kind: BlobKind, length: Int, segment: Int, offset: Long) =
            BlobRef(hash, kind, length).apply {
                this.segment = segment
                this.offset = offset
            }
    }
}
