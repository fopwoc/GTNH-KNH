package io.github.fopwoc.mods.palimpsest.tree

import java.nio.ByteBuffer
import java.util.zip.CRC32

/**
 * The bytes of a segment file: a header naming the writing machine and the segment's ordinal,
 * then commit groups — each a CRC-framed run of records — and, once sealed, a trailer that lists
 * the roots and the machine slots so a reader opens it without scanning.
 *
 * ```
 * header   MAGIC(8) version(u16) machine(u32) ordinal(u32)
 * group    'G' length(u32) payload crc32(u32)      payload = records: type(u8) length(varint) bytes
 * trailer  'T' length(u32) payload crc32(u32) length(u32) "TRLR"
 * ```
 *
 * A record's ref is the file offset of its type byte. Refs inside records name other segments by
 * a slot: slot 0 is the writing machine, others are declared by SLOT records as they are first
 * needed and repeated in the trailer.
 */
object SegmentFormat {
    val MAGIC: ByteArray = "PALIMTRE".toByteArray(Charsets.US_ASCII)
    val TRAILER_MAGIC: ByteArray = "TRLR".toByteArray(Charsets.US_ASCII)
    const val VERSION = 1
    const val HEADER_BYTES = 8 + 2 + 4 + 4
    const val GROUP = 'G'.code
    const val TRAILER = 'T'.code
    const val FRAME_BYTES = 1 + 4
    const val CRC_BYTES = 4
    const val TRAILER_TAIL_BYTES = 4 + 4

    enum class RecordType(val code: Int) {
        TILE(1),
        NODE(2),
        ROOT(3),
        SLOT(4);

        companion object {
            fun of(code: Int): RecordType =
                entries.firstOrNull { it.code == code } ?: throw CorruptTreeException("Unknown record type $code")
        }
    }

    class Header(val machineId: Int, val ordinal: Int)

    /** A root written at [offset] for [epoch]; the ref is resolved by whoever reads the segment. */
    class RootEntry(val epoch: Long, val offset: Int)

    class Trailer(val roots: List<RootEntry>, val slots: IntArray)

    fun header(machineId: Int, ordinal: Int): ByteArray =
        ByteSink(HEADER_BYTES)
            .apply {
                bytes(MAGIC)
                fixed(VERSION.toLong(), 2)
                fixed(machineId.toLong() and 0xFFFFFFFFL, 4)
                fixed(ordinal.toLong(), 4)
            }
            .toByteArray()

    fun readHeader(buffer: ByteBuffer): Header {
        if (buffer.limit() < HEADER_BYTES) throw CorruptTreeException("Segment shorter than its header")
        val source = ByteSource(buffer, 0, HEADER_BYTES)
        if (!source.bytes(MAGIC.size).contentEquals(MAGIC)) throw CorruptTreeException("Not a segment file")
        val version = source.fixed(2).toInt()
        if (version != VERSION) throw CorruptTreeException("Segment format $version, expected $VERSION")
        return Header(source.fixed(4).toInt(), source.fixed(4).toInt())
    }

    fun crc(buffer: ByteBuffer, from: Int, length: Int): Int {
        val crc = CRC32()
        crc.update(buffer.slice(from, length))
        return crc.value.toInt()
    }

    fun trailer(roots: List<RootEntry>, slots: IntArray): ByteArray {
        val payload =
            ByteSink()
                .apply {
                    varint(roots.size)
                    var epoch = 0L
                    for (root in roots) {
                        varint(root.epoch - epoch)
                        epoch = root.epoch
                        varint(root.offset)
                    }
                    varint(slots.size)
                    for (machine in slots) fixed(machine.toLong() and 0xFFFFFFFFL, 4)
                }
                .toByteArray()
        return ByteSink()
            .apply {
                byte(TRAILER)
                fixed(payload.size.toLong(), 4)
                bytes(payload)
                fixed(crc(ByteBuffer.wrap(payload), 0, payload.size).toLong() and 0xFFFFFFFFL, 4)
                fixed(payload.size.toLong(), 4)
                bytes(TRAILER_MAGIC)
            }
            .toByteArray()
    }

    /** The trailer of a sealed segment, or null when the file has none (an unsealed copy). */
    fun readTrailer(buffer: ByteBuffer): Trailer? {
        val size = buffer.limit()
        if (size < HEADER_BYTES + FRAME_BYTES + CRC_BYTES + TRAILER_TAIL_BYTES) return null
        val tail = ByteSource(buffer, size - TRAILER_TAIL_BYTES, size)
        val length = tail.fixed(4).toInt()
        if (!tail.bytes(TRAILER_MAGIC.size).contentEquals(TRAILER_MAGIC)) return null
        val start = size - TRAILER_TAIL_BYTES - CRC_BYTES - length - FRAME_BYTES
        if (length < 0 || start < HEADER_BYTES) throw CorruptTreeException("Trailer length $length")
        val frame = ByteSource(buffer, start, start + FRAME_BYTES)
        if (frame.byte() != TRAILER || frame.fixed(4).toInt() != length) throw CorruptTreeException("Trailer frame damaged")
        val payloadStart = start + FRAME_BYTES
        val crc = ByteSource(buffer, payloadStart + length, payloadStart + length + CRC_BYTES).fixed(4).toInt()
        if (crc != crc(buffer, payloadStart, length)) throw CorruptTreeException("Trailer checksum mismatch")
        val source = ByteSource(buffer, payloadStart, payloadStart + length)
        val rootCount = source.varintInt()
        var epoch = 0L
        val roots =
            List(rootCount) {
                epoch += source.varint()
                RootEntry(epoch, source.varintInt())
            }
        val slots = IntArray(source.varintInt()) { source.fixed(4).toInt() }
        return Trailer(roots, slots)
    }

    /** Where the trailer starts in a sealed file, so the group scan knows where to stop. */
    fun trailerStart(buffer: ByteBuffer): Int {
        val size = buffer.limit()
        val length = ByteSource(buffer, size - TRAILER_TAIL_BYTES, size).fixed(4).toInt()
        return size - TRAILER_TAIL_BYTES - CRC_BYTES - length - FRAME_BYTES
    }
}
