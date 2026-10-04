package io.github.fopwoc.palimpsest.db.index

import io.github.fopwoc.palimpsest.db.Commit
import io.github.fopwoc.palimpsest.db.WorldTick
import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.store.Frames
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

/**
 * The dimension's commits with the regions each one touched, appended in frames; like regions,
 * idempotent by tick and cut at a torn tail. The regions are what lets a diff between two moments
 * open only the places that changed. Thread-safe.
 */
internal class TimelineFile private constructor(private val file: Path) {
    private val commits = ArrayList<Commit>()
    private val touched = ArrayList<Array<RegionKey>>()
    private var saved = 0
    private var length = 0L

    @Synchronized fun commits(): List<Commit> = commits.toList()

    @Synchronized
    fun append(commit: Commit, regions: Collection<RegionKey>): Boolean {
        if (commits.isNotEmpty() && commits.last().tick >= commit.tick) return false
        commits += commit
        touched += regions.toTypedArray()
        return true
    }

    /** Regions touched by commits after [from] up to and including [to]. */
    @Synchronized
    fun regionsBetween(from: Long, to: Long): Set<RegionKey> {
        val regions = HashSet<RegionKey>()
        for (index in commits.indices) {
            val tick = commits[index].tick.value
            if (tick > from && tick <= to) regions += touched[index]
        }
        return regions
    }

    /** Appends the commits added since the last save; returns the file's length. */
    @Synchronized
    fun save(): Long {
        if (saved == commits.size) return length
        val sink = ByteSink((commits.size - saved) * 32)
        sink.varint(commits.size - saved)
        for (index in saved until commits.size) {
            val commit = commits[index]
            sink.signed(commit.tick.value)
            sink.varint(commit.observedAt)
            sink.varint(commit.chunksChanged)
            sink.varint(commit.sectionsWritten)
            sink.varint(commit.bytes)
            sink.varint(touched[index].size)
            for (key in touched[index]) {
                sink.signed(key.x.toLong())
                sink.signed(key.z.toLong())
            }
        }
        FileChannel.open(file, CREATE, WRITE).use { channel ->
            length += Frames.write(channel, length, sink.toByteArray())
        }
        saved = commits.size
        return length
    }

    companion object {
        /** A fresh timeline holding just [commits] with the regions each touched. */
        fun replace(file: Path, commits: List<Pair<Commit, Collection<RegionKey>>>): TimelineFile {
            Files.deleteIfExists(file)
            return TimelineFile(file).apply {
                commits.forEach { (commit, regions) -> append(commit, regions) }
                save()
            }
        }

        fun load(file: Path): TimelineFile {
            val timeline = TimelineFile(file)
            timeline.length =
                Frames.readCuttingTail(file) { payload ->
                    val source = ByteSource(payload)
                    repeat(source.varintInt()) {
                        val commit =
                            Commit(
                                WorldTick(source.signed()),
                                source.varint(),
                                source.varintInt(),
                                source.varintInt(),
                                source.varint(),
                            )
                        val regions =
                            List(source.varintInt()) {
                                RegionKey(source.signed().toInt(), source.signed().toInt())
                            }
                        timeline.append(commit, regions)
                    }
                }
            timeline.saved = timeline.commits.size
            return timeline
        }
    }
}
