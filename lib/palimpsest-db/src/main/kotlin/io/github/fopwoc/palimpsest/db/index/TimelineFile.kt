package io.github.fopwoc.palimpsest.db.index

import io.github.fopwoc.palimpsest.db.Commit
import io.github.fopwoc.palimpsest.db.WorldTick
import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.store.Frames
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE

/**
 * The dimension's commits, appended in frames; like regions, idempotent by tick and cut at a torn
 * tail.
 */
internal class TimelineFile
private constructor(
    private val file: Path,
    private var length: Long,
    val commits: MutableList<Commit>,
) {
    private val unsaved = ArrayList<Commit>()

    /** Writer thread only. */
    fun append(commit: Commit): Boolean {
        if (commits.isNotEmpty() && commits.last().tick >= commit.tick) return false
        commits += commit
        unsaved += commit
        return true
    }

    /** Appends the commits added since the last save; returns the file's length. */
    fun save(): Long {
        if (unsaved.isEmpty()) return length
        val sink = ByteSink(unsaved.size * 24)
        sink.varint(unsaved.size)
        for (commit in unsaved) {
            sink.signed(commit.tick.value)
            sink.varint(commit.observedAt)
            sink.varint(commit.chunksChanged)
            sink.varint(commit.sectionsWritten)
            sink.varint(commit.bytes)
        }
        FileChannel.open(file, CREATE, WRITE).use { channel ->
            length += Frames.write(channel, length, sink.toByteArray())
        }
        unsaved.clear()
        return length
    }

    companion object {
        fun load(file: Path): TimelineFile {
            val commits = ArrayList<Commit>()
            if (!Files.exists(file)) return TimelineFile(file, 0, commits)
            var at = 0L
            FileChannel.open(file, READ, WRITE).use { channel ->
                while (at < channel.size()) {
                    val payload =
                        try {
                            Frames.read(channel, at)
                        } catch (_: IOException) {
                            channel.truncate(at)
                            break
                        }
                    val source = ByteSource(payload)
                    repeat(source.varintInt()) {
                        commits +=
                            Commit(
                                WorldTick(source.signed()),
                                source.varint(),
                                source.varintInt(),
                                source.varintInt(),
                                source.varint(),
                            )
                    }
                    at += Frames.HEADER + payload.size
                }
            }
            return TimelineFile(file, at, commits)
        }
    }
}
