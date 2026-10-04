package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.CorruptDataException

/**
 * The tail of a commit frame, after its blobs: the moment, the lengths of the blobs the frame
 * introduced (in layout order) with the base of each delta, and every chunk patch. A slot reference
 * is 0 for air, odd for a blob of this frame, even for one stored earlier, followed by its offset
 * and length. Content hashes are not truth: the index keeps them and recomputes them when it is
 * rebuilt.
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

    /**
     * [bases] and [baseLengths] give, per blob of the frame, the base of a delta or
     * [Positions.AIR].
     */
    class Decoded(
        val tick: Long,
        val observedAt: Long,
        val blobs: LongArray,
        val blobLengths: IntArray,
        val bases: LongArray,
        val baseLengths: IntArray,
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
            base(sink, blob.basePosition, blob.baseLength)
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

    /**
     * Writes a record from stored positions rather than blobs in flight, for rewriting history:
     * [blobs] are the positions this frame's blobs will have, in layout order.
     */
    fun encodeStored(
        sink: ByteSink,
        tick: Long,
        observedAt: Long,
        blobs: LongArray,
        blobLengths: IntArray,
        bases: LongArray,
        baseLengths: IntArray,
        patches: List<Patch>,
    ) {
        sink.signed(tick)
        sink.varint(observedAt)
        sink.varint(blobs.size)
        val local = HashMap<Long, Int>(blobs.size * 2)
        blobs.forEachIndexed { index, position ->
            local[position] = index
            sink.varint(blobLengths[index])
            base(sink, bases[index], baseLengths[index])
        }
        sink.varint(patches.size)
        for (patch in patches) {
            sink.signed(patch.pos.x.toLong())
            sink.signed(patch.pos.z.toLong())
            sink.signed(patch.minSection.toLong())
            sink.varint(patch.slots)
            sink.varint(patch.mask)
            for (slot in 0 until patch.slots) {
                if (patch.mask and (1L shl slot) == 0L) continue
                val position = patch.positions[slot]
                val index = local[position]
                when {
                    position == Positions.AIR -> sink.varint(0)
                    index != null -> sink.varint(index * 2L + 1)
                    else -> {
                        sink.varint(Positions.segment(position) * 2L + 2)
                        sink.varint(Positions.offset(position))
                        sink.varint(patch.lengths[slot])
                    }
                }
            }
        }
    }

    /** A delta's base: 0 for a full blob, else its segment as an even tag, offset and length. */
    private fun base(sink: ByteSink, position: Long, length: Int) {
        if (position == Positions.AIR) {
            sink.varint(0)
            return
        }
        sink.varint(Positions.segment(position) * 2L + 2)
        sink.varint(Positions.offset(position))
        sink.varint(length)
    }

    /** [blobsStart] is the file offset of the frame's first blob in segment [segment]. */
    fun decode(source: ByteSource, segment: Int, blobsStart: Long): Decoded {
        val tick = source.signed()
        val observedAt = source.varint()
        val count = source.varintInt()
        val blobs = LongArray(count)
        val blobLengths = IntArray(count)
        val bases = LongArray(count) { Positions.AIR }
        val baseLengths = IntArray(count)
        var offset = blobsStart
        for (index in 0 until count) {
            blobLengths[index] = source.varintInt()
            blobs[index] = Positions.of(segment, offset)
            offset += blobLengths[index]
            val tag = source.varint()
            if (tag != 0L) {
                bases[index] = Positions.of(((tag - 2) / 2).toInt(), source.varint())
                baseLengths[index] = source.varintInt()
            }
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
        return Decoded(tick, observedAt, blobs, blobLengths, bases, baseLengths, patches)
    }
}
