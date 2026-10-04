package io.github.fopwoc.palimpsest.db.index

import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.CorruptDataException
import io.github.fopwoc.palimpsest.db.codec.string
import io.github.fopwoc.palimpsest.db.store.Frames
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/**
 * How much of the dimension's truth the index holds: per segment, in truth order, the bytes already
 * applied, and the length each index file had when they were. Written last on every index flush, by
 * atomic rename and the only fsync of the index: region files are not synced, so after a system
 * crash a file shorter than its recorded length shows the index is incomplete. Segment names carry
 * a session key, so another computer's history never matches by accident.
 */
internal class Coverage(val segments: List<Segment>, val files: Map<String, Long>) {
    data class Segment(val name: String, val length: Long)

    /** Whether every index file still holds at least the bytes recorded for it. */
    fun intact(directory: Path): Boolean = files.all { (name, length) ->
        val file = directory.resolve(name)
        Files.exists(file) && Files.size(file) >= length
    }

    fun write(directory: Path) {
        val sink = ByteSink(64 + segments.size * 32)
        sink.varint(FORMAT)
        sink.varint(segments.size)
        for (segment in segments) {
            sink.string(segment.name)
            sink.varint(segment.length)
        }
        sink.varint(files.size)
        for ((name, length) in files) {
            sink.string(name)
            sink.varint(length)
        }
        val draft = directory.resolve("$FILE.draft")
        FileChannel.open(draft, CREATE, WRITE, TRUNCATE_EXISTING).use { channel ->
            Frames.write(channel, 0, sink.toByteArray())
            channel.force(true)
        }
        Files.move(draft, directory.resolve(FILE), ATOMIC_MOVE, REPLACE_EXISTING)
    }

    companion object {
        /** Bumped whenever the index format changes; any other value means a rebuild. */
        const val FORMAT = 7
        private const val FILE = "coverage"

        /**
         * The stored coverage, or null when there is none or it is unreadable or of another format.
         */
        fun read(directory: Path): Coverage? {
            val file = directory.resolve(FILE)
            if (!Files.exists(file)) return null
            return try {
                FileChannel.open(file, READ).use { channel ->
                    val source = ByteSource(Frames.read(channel, 0))
                    if (source.varintInt() != FORMAT) return null
                    val segments =
                        List(source.varintInt()) { Segment(source.string(), source.varint()) }
                    Coverage(
                        segments,
                        (0 until source.varintInt()).associate {
                            source.string() to source.varint()
                        },
                    )
                }
            } catch (_: CorruptDataException) {
                null
            } catch (_: IOException) {
                null
            }
        }
    }
}
