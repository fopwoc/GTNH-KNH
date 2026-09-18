package io.github.fopwoc.mods.palimpsest.storage

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.CRC32

/**
 * Local, append-only log of segment images that have not been sealed into a content-addressed
 * segment yet. Each frame is a length, a CRC32 and the image; replay stops at the first frame that
 * is truncated or fails its checksum, which is how a crash mid-write is recovered.
 */
internal class WriteAheadLog(private val file: Path) : AutoCloseable {
    class Frame(val offset: Long, val length: Int)

    private var writer: FileChannel? = null
    private var readChannel: FileChannel? = null

    /** Read channel for positioned reads; the log file must exist by the time it is used. */
    val reader: FileChannel
        get() =
            readChannel ?: FileChannel.open(file, StandardOpenOption.READ).also { readChannel = it }

    val exists: Boolean
        get() = Files.isRegularFile(file)

    var bytes: Long = if (exists) Files.size(file) else 0L
        private set

    /** Frames whose checksum verifies, in order; a damaged tail is truncated away. */
    fun replay(): List<Frame> {
        if (!exists || bytes == 0L) return emptyList()
        val frames = ArrayList<Frame>()
        val header = ByteBuffer.allocate(HEADER_BYTES)
        var position = 0L
        while (position + HEADER_BYTES <= bytes) {
            header.clear()
            readFully(reader, position, header)
            val length = header.getInt(0)
            val expected = header.getInt(Int.SIZE_BYTES)
            if (length <= 0 || position + HEADER_BYTES + length > bytes) break
            val image = ByteBuffer.allocate(length)
            readFully(reader, position + HEADER_BYTES, image)
            val crc = CRC32().also { it.update(image.array()) }
            if (crc.value.toInt() != expected) break
            frames += Frame(position + HEADER_BYTES, length)
            position += HEADER_BYTES + length
        }
        if (position != bytes) {
            openWriter().truncate(position)
            bytes = position
        }
        return frames
    }

    /** Appends one image durably and returns where its first byte landed. */
    fun append(image: ByteArray): Long {
        val output = openWriter()
        val crc = CRC32().also { it.update(image) }
        val frame =
            ByteBuffer.allocate(HEADER_BYTES + image.size)
                .putInt(image.size)
                .putInt(crc.value.toInt())
                .put(image)
        frame.flip()
        var position = bytes
        while (frame.hasRemaining()) position += output.write(frame, position)
        output.force(false)
        val offset = bytes + HEADER_BYTES
        bytes = position
        return offset
    }

    fun clear() {
        if (bytes == 0L && !exists) return
        openWriter().truncate(0).force(false)
        bytes = 0
    }

    private fun openWriter(): FileChannel =
        writer
            ?: run {
                Files.createDirectories(file.parent)
                FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE).also {
                    writer = it
                }
            }

    override fun close() {
        writer?.close()
        writer = null
        readChannel?.close()
        readChannel = null
    }

    private companion object {
        const val HEADER_BYTES = Int.SIZE_BYTES * 2

        fun readFully(channel: FileChannel, position: Long, buffer: ByteBuffer) {
            var offset = position
            while (buffer.hasRemaining()) {
                val count = channel.read(buffer, offset)
                if (count <= 0) throw IOException("Unexpected end of write-ahead log")
                offset += count
            }
        }
    }
}
