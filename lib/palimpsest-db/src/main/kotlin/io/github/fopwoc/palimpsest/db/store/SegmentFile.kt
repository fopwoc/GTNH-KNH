package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.CorruptDataException
import io.github.fopwoc.palimpsest.db.codec.string
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE

/**
 * Everything one session wrote to one dimension: a header frame, then one frame per commit. Only
 * the session that created it appends; once sealed it never changes, so readers on any thread use
 * positional reads without locks.
 */
internal class SegmentFile
private constructor(
    val ordinal: Int,
    val name: String,
    private val channel: FileChannel,
    length: Long,
) : AutoCloseable {
    /** Bytes written so far; appended by the writer thread only. */
    @Volatile
    var length: Long = length
        private set

    fun read(offset: Long, length: Int): ByteArray = channel.readFully(offset, length)

    /** Appends [payload] as one frame; returns the payload's file offset. Writer thread only. */
    fun append(payload: ByteArray): Long {
        val at = length
        length += Frames.write(channel, at, payload)
        return at + Frames.HEADER
    }

    fun force() = channel.force(false)

    /** Payload length of the frame at [position]. */
    fun frameLength(position: Long): Int = Frames.length(channel, position)

    override fun close() = channel.close()

    companion object {
        /** "PSEG" */
        private const val MAGIC = 0x50534547

        fun create(
            path: Path,
            ordinal: Int,
            generation: Int,
            dimension: DimensionId,
            session: Manifest.Session,
        ): SegmentFile {
            Files.createDirectories(path.parent)
            val channel = FileChannel.open(path, CREATE_NEW, READ, WRITE)
            val header =
                ByteSink(64).apply {
                    fixed(MAGIC.toLong(), 4)
                    varint(generation)
                    string(dimension.key)
                    string(session.toString())
                }
            val length = Frames.write(channel, 0, header.toByteArray()).toLong()
            return SegmentFile(ordinal, path.fileName.toString(), channel, length)
        }

        /**
         * Opens an earlier session's segment for reading up to [length], the bytes its manifest
         * committed; a crashed session's tail past it is never read.
         */
        fun open(path: Path, ordinal: Int, length: Long): SegmentFile {
            val channel = FileChannel.open(path, READ)
            val source = ByteSource(Frames.read(channel, 0))
            if (source.fixed(4).toInt() != MAGIC) throw CorruptDataException("Not a segment: $path")
            return SegmentFile(ordinal, path.fileName.toString(), channel, length)
        }

        /** Size of the header frame, where the first commit frame starts. */
        fun firstFrame(segment: SegmentFile): Long = Frames.HEADER + segment.frameLength(0).toLong()
    }
}
