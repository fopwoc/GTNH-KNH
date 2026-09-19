package io.github.fopwoc.mods.palimpsest.tree

import java.nio.ByteBuffer

/**
 * Read side of one segment, sealed (memory-mapped) or the machine's active one (the writer's
 * buffer). Records are addressed by offset; [scan] walks every valid group, which is how an
 * unsealed segment finds its roots and slots and how a torn tail is measured.
 */
abstract class SegmentReader {
    abstract val machineId: Int
    abstract val ordinal: Int

    /** Machine id per slot; slot 0 is the writer. Grows on the active segment. */
    abstract val slots: IntArray

    /**
     * The bytes and how many of them readers may look at: the whole file up to the trailer when
     * sealed, the published length when active. Taken as one snapshot.
     */
    protected abstract fun view(): Pair<ByteBuffer, Int>

    class Record(val type: SegmentFormat.RecordType, val source: ByteSource)

    fun record(offset: Int): Record {
        val (buffer, limit) = view()
        if (offset < SegmentFormat.HEADER_BYTES || offset >= limit) throw CorruptTreeException("Ref offset $offset outside segment")
        val head = ByteSource(buffer, offset, limit)
        val type = SegmentFormat.RecordType.of(head.byte())
        val length = head.varintInt()
        val start = head.position
        if (start + length > limit) throw CorruptTreeException("Record at $offset runs past the segment")
        return Record(type, ByteSource(buffer, start, start + length))
    }

    /** Every valid group's records in order; returns the offset just past the last valid group. */
    fun scan(from: Int = SegmentFormat.HEADER_BYTES, visit: (Int, Record) -> Unit): Int {
        val (buffer, to) = view()
        var position = from
        while (true) {
            val length = validGroupLength(buffer, position, to) ?: return position
            val payload = position + SegmentFormat.FRAME_BYTES
            var offset = payload
            while (offset < payload + length) {
                val head = ByteSource(buffer, offset, payload + length)
                val type = SegmentFormat.RecordType.of(head.byte())
                val recordLength = head.varintInt()
                val start = head.position
                if (start + recordLength > payload + length) throw CorruptTreeException("Record at $offset overruns its group")
                visit(offset, Record(type, ByteSource(buffer, start, start + recordLength)))
                offset = start + recordLength
            }
            position = payload + length + SegmentFormat.CRC_BYTES
        }
    }

    /** Payload length of the group at [position] when its frame and checksum hold; else null. */
    private fun validGroupLength(buffer: ByteBuffer, position: Int, to: Int): Int? {
        if (position + SegmentFormat.FRAME_BYTES + SegmentFormat.CRC_BYTES > to) return null
        val frame = ByteSource(buffer, position, to)
        if (frame.byte() != SegmentFormat.GROUP) return null
        val length = frame.fixed(4).toInt()
        val payload = position + SegmentFormat.FRAME_BYTES
        if (length < 0 || payload + length + SegmentFormat.CRC_BYTES > to) return null
        val crc = ByteSource(buffer, payload + length, payload + length + SegmentFormat.CRC_BYTES).fixed(4).toInt()
        return length.takeIf { crc == SegmentFormat.crc(buffer, payload, length) }
    }

    /** Sealed file on disk, mapped read-only; roots and slots come from the trailer. */
    class Sealed(private val mapped: ByteBuffer, header: SegmentFormat.Header, val trailer: SegmentFormat.Trailer) : SegmentReader() {
        override val machineId: Int = header.machineId
        override val ordinal: Int = header.ordinal
        override val slots: IntArray = intArrayOf(machineId) + trailer.slots
        private val end = SegmentFormat.trailerStart(mapped)

        override fun view(): Pair<ByteBuffer, Int> = mapped to end
    }
}
