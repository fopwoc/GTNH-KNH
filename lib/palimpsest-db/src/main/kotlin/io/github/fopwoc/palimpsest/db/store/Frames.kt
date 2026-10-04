package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.codec.CorruptDataException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.zip.CRC32C

/**
 * The unit every append-only file is made of: a 4-byte payload length, a 4-byte CRC32C of the
 * payload, then the payload. A torn write shows up as a short or failing frame, never as data.
 */
internal object Frames {
    const val HEADER = 8

    /** Appends [payload] as one frame at [position]; returns the frame's size on disk. */
    fun write(channel: FileChannel, position: Long, payload: ByteArray): Int {
        val buffer = ByteBuffer.allocate(HEADER + payload.size)
        buffer.putInt(payload.size).putInt(crc(payload)).put(payload).flip()
        channel.writeFully(buffer, position)
        return HEADER + payload.size
    }

    /** The payload of the frame at [position], checked against its CRC. */
    fun read(channel: FileChannel, position: Long): ByteArray {
        val header = ByteBuffer.wrap(channel.readFully(position, HEADER))
        val length = header.int
        val expected = header.int
        if (length < 0) throw CorruptDataException("Negative frame length at $position")
        val payload = channel.readFully(position + HEADER, length)
        if (crc(payload) != expected)
            throw CorruptDataException("Frame checksum mismatch at $position")
        return payload
    }

    /** Payload length of the frame at [position], without reading or checking the payload. */
    fun length(channel: FileChannel, position: Long): Int {
        val length = ByteBuffer.wrap(channel.readFully(position, HEADER)).int
        if (length < 0) throw CorruptDataException("Negative frame length at $position")
        return length
    }

    private fun crc(payload: ByteArray): Int = CRC32C().apply { update(payload) }.value.toInt()
}
