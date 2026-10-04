package io.github.fopwoc.mods.palimpsest.prototype.volume.storage.tree

import io.github.fopwoc.mods.palimpsest.tree.ByteSink
import io.github.fopwoc.mods.palimpsest.tree.ByteSource
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/**
 * One append-only file of records. A ref packs a record's offset and length into a Long, 0 is null.
 * Records written during a commit stay in memory until [flush], and remain readable. Inside
 * records, refs are stored as backward distances, which stay short for recent targets.
 */
class Pack(path: Path) : AutoCloseable {
    private val channel = FileChannel.open(path, CREATE, READ, WRITE, TRUNCATE_EXISTING)
    private val pending = ByteSink(64 * 1024)
    private var flushed = 0L

    val bytes: Long
        get() = flushed + pending.size

    /** The offset the next record will get; the origin for backward refs inside it. */
    val next: Long
        get() = bytes

    fun append(record: ByteArray): Long {
        val ref = (next shl LENGTH_BITS) or record.size.toLong()
        pending.bytes(record)
        return ref
    }

    fun read(ref: Long): ByteArray {
        val offset = offset(ref)
        val length = length(ref)
        if (offset >= flushed) {
            val start = (offset - flushed).toInt()
            return pending.toByteArray().copyOfRange(start, start + length)
        }
        val buffer = ByteBuffer.allocate(length)
        var position = offset
        while (buffer.hasRemaining()) position += channel.read(buffer, position)
        return buffer.array()
    }

    fun flush() {
        val buffer = ByteBuffer.wrap(pending.toByteArray())
        while (buffer.hasRemaining()) flushed += channel.write(buffer, flushed)
        pending.clear()
    }

    override fun close() = channel.close()

    companion object {
        private const val LENGTH_BITS = 24

        fun offset(ref: Long): Long = ref ushr LENGTH_BITS

        fun length(ref: Long): Int = (ref and ((1L shl LENGTH_BITS) - 1)).toInt()

        /** Writes [ref] relative to [origin], the offset of the record being encoded. */
        fun writeRef(sink: ByteSink, ref: Long, origin: Long) {
            if (ref == 0L) {
                sink.varint(0)
                return
            }
            sink.varint(origin - offset(ref))
            sink.varint(length(ref))
        }

        fun readRef(source: ByteSource, origin: Long): Long {
            val distance = source.varint()
            if (distance == 0L) return 0
            return ((origin - distance) shl LENGTH_BITS) or source.varint()
        }
    }
}
