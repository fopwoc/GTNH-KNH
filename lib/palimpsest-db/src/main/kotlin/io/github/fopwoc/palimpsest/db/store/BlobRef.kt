package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.codec.ContentHash

/**
 * One piece of content on its way through a commit. A fresh blob carries its encoded [pending]
 * bytes until the writer lays it out and sets [position]; a stored one already has it. Readers
 * never see a blob before its commit is published.
 */
internal class BlobRef(val hash: ContentHash, val kind: BlobKind, val length: Int) {
    var pending: ByteArray? = null
    var position: Long = Positions.AIR

    val positioned: Boolean
        get() = position != Positions.AIR

    companion object {
        fun fresh(hash: ContentHash, kind: BlobKind, bytes: ByteArray) =
            BlobRef(hash, kind, bytes.size).apply { pending = bytes }

        fun stored(hash: ContentHash, kind: BlobKind, length: Int, position: Long) =
            BlobRef(hash, kind, length).apply { this.position = position }
    }
}
