package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.ContentHash
import io.github.fopwoc.palimpsest.db.codec.CorruptDataException

/**
 * The tail of a commit frame, after its blobs: the moment, the blobs the frame introduced (in
 * layout order, with their hashes so deduplication survives a reopen) and every chunk patch. A slot
 * reference is 0 for air, odd for a blob of this frame, even for one stored earlier, followed by
 * its segment ordinal, offset and length.
 */
internal object CommitRecord {
    class Decoded(
        val tick: Long,
        val observedAt: Long,
        val blobs: List<BlobRef>,
        val patches: List<ChunkPatch>,
    )

    fun encode(
        sink: ByteSink,
        tick: Long,
        observedAt: Long,
        blobs: List<BlobRef>,
        patches: List<ChunkPatch>,
    ) {
        sink.signed(tick)
        sink.varint(observedAt)
        sink.varint(blobs.size)
        val local = java.util.IdentityHashMap<BlobRef, Int>(blobs.size * 2)
        blobs.forEachIndexed { index, blob ->
            local[blob] = index
            sink.byte(blob.kind.ordinal)
            sink.varint(blob.length)
            sink.fixed(blob.hash.high, 8)
            sink.fixed(blob.hash.low, 8)
        }
        sink.varint(patches.size)
        for (patch in patches) {
            sink.signed(patch.pos.x.toLong())
            sink.signed(patch.pos.z.toLong())
            sink.signed(patch.minSection.toLong())
            sink.varint(patch.slots.size)
            sink.varint(patch.mask)
            patch.slots.forEachIndexed { slot, blob ->
                if (patch.mask and (1L shl slot) == 0L) return@forEachIndexed
                val index = blob?.let(local::get)
                when {
                    blob == null -> sink.varint(0)
                    index != null -> sink.varint(index * 2L + 1)
                    else -> {
                        check(blob.positioned) { "Blob referenced before it was written" }
                        sink.varint(blob.segment * 2L + 2)
                        sink.varint(blob.offset)
                        sink.varint(blob.length)
                    }
                }
            }
        }
    }

    /**
     * [blobsStart] is the file offset of the frame's first blob in segment [segment]; [previous]
     * gives a chunk's slots before this commit and [stored] finds an earlier blob by position.
     */
    fun decode(
        source: ByteSource,
        segment: Int,
        blobsStart: Long,
        previous: (ChunkPos) -> Array<BlobRef?>?,
        stored: (segment: Int, offset: Long) -> BlobRef,
    ): Decoded {
        val tick = source.signed()
        val observedAt = source.varint()
        var offset = blobsStart
        val blobs =
            List(source.varintInt()) {
                val kind =
                    BlobKind.entries.getOrNull(source.byte())
                        ?: throw CorruptDataException("Unknown blob kind")
                val length = source.varintInt()
                val hash = ContentHash(source.fixed(8), source.fixed(8))
                BlobRef.stored(hash, kind, length, segment, offset).also { offset += length }
            }
        val patches =
            List(source.varintInt()) {
                val pos = ChunkPos(source.signed().toInt(), source.signed().toInt())
                val minSection = source.signed().toInt()
                val count = source.varintInt()
                val mask = source.varint()
                val slots =
                    previous(pos)?.takeIf { it.size == count }?.copyOf() ?: arrayOfNulls(count)
                for (slot in 0 until count) {
                    if (mask and (1L shl slot) == 0L) continue
                    val tag = source.varint()
                    slots[slot] =
                        when {
                            tag == 0L -> null
                            tag % 2 == 1L ->
                                blobs.getOrNull(((tag - 1) / 2).toInt())
                                    ?: throw CorruptDataException("Bad local blob")
                            else ->
                                stored(((tag - 2) / 2).toInt(), source.varint()).also {
                                    source.varint()
                                }
                        }
                }
                ChunkPatch(pos, minSection, mask, slots)
            }
        return Decoded(tick, observedAt, blobs, patches)
    }
}
