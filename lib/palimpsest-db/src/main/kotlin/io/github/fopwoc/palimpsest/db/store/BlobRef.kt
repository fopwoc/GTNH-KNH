package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.codec.ContentHash

/**
 * One piece of content on its way through a commit. A fresh blob carries its encoded [pending]
 * bytes until the writer lays it out and sets [position]; a stored one already has it. A delta
 * holds only the changes from its [basePosition], an earlier version of the same chunk's section,
 * [depth] links down a chain that ends in a full section. Readers never see a blob before its
 * commit is published.
 */
internal class BlobRef(val hash: ContentHash, val kind: BlobKind, val length: Int) {
    var pending: ByteArray? = null
    var position: Long = Positions.AIR
    var basePosition: Long = Positions.AIR
    var baseLength: Int = 0
    var depth: Int = 0

    val positioned: Boolean
        get() = position != Positions.AIR

    val delta: Boolean
        get() = basePosition != Positions.AIR

    companion object {
        fun fresh(hash: ContentHash, kind: BlobKind, bytes: ByteArray) =
            BlobRef(hash, kind, bytes.size).apply { pending = bytes }

        /** A fresh section delta against [base], which must be stored already. */
        fun delta(hash: ContentHash, bytes: ByteArray, base: BlobRef, baseDepth: Int) =
            fresh(hash, BlobKind.SECTION, bytes).apply {
                check(base.positioned) { "A delta's base must be stored" }
                basePosition = base.position
                baseLength = base.length
                depth = baseDepth + 1
            }

        fun stored(
            hash: ContentHash,
            kind: BlobKind,
            length: Int,
            position: Long,
            basePosition: Long = Positions.AIR,
            baseLength: Int = 0,
        ) =
            BlobRef(hash, kind, length).apply {
                this.position = position
                this.basePosition = basePosition
                this.baseLength = baseLength
            }
    }
}
