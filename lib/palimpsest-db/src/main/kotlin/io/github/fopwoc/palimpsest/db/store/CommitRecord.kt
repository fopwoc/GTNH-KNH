package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.CorruptDataException

/**
 * The tail of a commit frame, after its blobs: the moment, the lengths of the blobs the frame
 * introduced (in layout order) and every chunk patch. A slot reference is 0 for air, odd for a blob
 * of this frame, even for one stored earlier, followed by its offset and length. Content hashes are
 * not truth: the index keeps them and recomputes them when it is rebuilt.
 */
internal object CommitRecord {
    /**
     * A patch as stored: for each slot in [mask], its blob [positions] (or [Positions.AIR]) and
     * [lengths].
     */
    class Patch(
        val pos: ChunkPos,
        val minSection: Int,
        val slots: Int,
        val mask: Long,
        val positions: LongArray,
        val lengths: IntArray,
    )

    class Decoded(
        val tick: Long,
        val observedAt: Long,
        val blobs: LongArray,
        val blobLengths: IntArray,
        val patches: List<Patch>,
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
            sink.varint(blob.length)
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
                        sink.varint(Positions.segment(blob.position) * 2L + 2)
                        sink.varint(Positions.offset(blob.position))
                        sink.varint(blob.length)
                    }
                }
            }
        }
    }

    /** [blobsStart] is the file offset of the frame's first blob in segment [segment]. */
    fun decode(source: ByteSource, segment: Int, blobsStart: Long): Decoded {
        val tick = source.signed()
        val observedAt = source.varint()
        val count = source.varintInt()
        val blobs = LongArray(count)
        val blobLengths = IntArray(count)
        var offset = blobsStart
        for (index in 0 until count) {
            blobLengths[index] = source.varintInt()
            blobs[index] = Positions.of(segment, offset)
            offset += blobLengths[index]
        }
        val patches =
            List(source.varintInt()) {
                val pos = ChunkPos(source.signed().toInt(), source.signed().toInt())
                val minSection = source.signed().toInt()
                val slots = source.varintInt()
                if (slots !in 1..Long.SIZE_BITS)
                    throw CorruptDataException("Chunk with $slots slots")
                val mask = source.varint()
                val positions = LongArray(slots) { Positions.AIR }
                val lengths = IntArray(slots)
                for (slot in 0 until slots) {
                    if (mask and (1L shl slot) == 0L) continue
                    val tag = source.varint()
                    when {
                        tag == 0L -> Unit
                        tag % 2 == 1L -> {
                            val index = ((tag - 1) / 2).toInt()
                            if (index !in 0 until count)
                                throw CorruptDataException("Bad local blob")
                            positions[slot] = blobs[index]
                            lengths[slot] = blobLengths[index]
                        }
                        else -> {
                            positions[slot] = Positions.of(((tag - 2) / 2).toInt(), source.varint())
                            lengths[slot] = source.varintInt()
                        }
                    }
                }
                Patch(pos, minSection, slots, mask, positions, lengths)
            }
        return Decoded(tick, observedAt, blobs, blobLengths, patches)
    }
}
