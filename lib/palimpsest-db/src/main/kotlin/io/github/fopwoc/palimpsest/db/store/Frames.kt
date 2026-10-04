package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.codec.CorruptDataException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
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

    /**
     * Every frame from [from] until [until], in order; a damaged one throws. Returns where they
     * end.
     */
    fun readAll(
        channel: FileChannel,
        until: Long,
        from: Long = 0,
        action: (ByteArray) -> Unit,
    ): Long {
        var at = from
        while (at < until) {
            val payload = read(channel, at)
            action(payload)
            at += HEADER + payload.size
        }
        return at
    }

    /**
     * Every whole frame of a rebuildable file, cutting it at the first torn or damaged one: what a
     * crash left after the last flush, which replaying history restores. Returns the length kept, 0
     * when the file does not exist.
     */
    fun readCuttingTail(path: Path, action: (ByteArray) -> Unit): Long {
        if (!Files.exists(path)) return 0
        FileChannel.open(path, READ, WRITE).use { channel ->
            var at = 0L
            while (at < channel.size()) {
                val payload =
                    try {
                        read(channel, at)
                    } catch (_: IOException) {
                        channel.truncate(at)
                        break
                    }
                action(payload)
                at += HEADER + payload.size
            }
            return at
        }
    }

    private fun crc(payload: ByteArray): Int = CRC32C().apply { update(payload) }.value.toInt()
}
