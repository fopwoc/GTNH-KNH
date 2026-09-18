package io.github.fopwoc.mods.palimpsest.storage

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.apache.logging.log4j.LogManager

/**
 * Hashes each content-addressed segment once per process. Segments are immutable, so a segment
 * whose size and modification time have not changed since its last verification is trusted.
 */
internal object SegmentVerifier {
    private val logger = LogManager.getLogger(SegmentVerifier::class.java)

    private data class Stamp(val size: Long, val modified: FileTime)

    private val verified = ConcurrentHashMap<Path, Stamp>()

    /** Returns true when the segment was hashed by this call rather than trusted. */
    fun verify(channel: FileChannel, file: Path, size: Long, magic: ByteArray): Boolean {
        val path = file.toAbsolutePath()
        val stamp = Stamp(size, Files.getLastModifiedTime(path))
        val hashed = verified[path] != stamp
        if (hashed) {
            hash(channel, file, size)
            verified[path] = stamp
        }
        checkMagic(channel, file, size, magic)
        return hashed
    }

    private fun hash(channel: FileChannel, file: Path, size: Long) {
        val digest = MessageDigest.getInstance("SHA-256")
        val content = ByteBuffer.allocate(1 shl 16)
        var hashed = 0L
        while (hashed < size) {
            content.clear()
            content.limit(minOf(content.capacity().toLong(), size - hashed).toInt())
            val count = channel.read(content, hashed)
            if (count <= 0) throw CorruptHistoryException("Incomplete segment $file")
            digest.update(content.array(), 0, count)
            hashed += count
        }
        val expected = digest.digest().joinToString("") { "%02x".format(it) }
        if (!file.fileName.toString().startsWith(expected)) {
            throw CorruptHistoryException("Segment hash mismatch: $file")
        }
        logger.debug("Verified segment {} ({} bytes)", file.fileName, size)
    }

    private fun checkMagic(channel: FileChannel, file: Path, size: Long, magic: ByteArray) {
        if (size < magic.size) throw CorruptHistoryException("Truncated segment $file")
        val header = ByteBuffer.allocate(magic.size)
        var offset = 0L
        while (header.hasRemaining()) {
            val count = channel.read(header, offset)
            if (count <= 0) throw IOException("Unexpected end of segment $file")
            offset += count
        }
        if (!header.array().contentEquals(magic)) {
            throw CorruptHistoryException("Unsupported segment format $file")
        }
    }
}
