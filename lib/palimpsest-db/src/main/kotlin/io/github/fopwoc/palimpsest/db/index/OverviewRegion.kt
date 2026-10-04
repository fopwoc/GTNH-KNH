package io.github.fopwoc.palimpsest.db.index

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
 * The far-zoom history of one region: per chunk, each time its sample changed, the tick and the
 * sample (block, height, depth, biome of its center column). Tiny next to the full region index, so
 * a wide view loads hundreds of these. Like the region index: append-only frames, idempotent by
 * tick, a torn tail cut on load. Thread-safe.
 */
internal class OverviewRegion private constructor(val key: RegionKey, private val file: Path) {
    private val ticks = arrayOfNulls<LongArray>(RegionKey.CHUNKS)
    private val samples = arrayOfNulls<IntArray>(RegionKey.CHUNKS)
    private val unsaved = ArrayList<Pair<Int, Int>>()
    private var fileLength = 0L

    @get:Synchronized
    val dirty: Boolean
        get() = unsaved.isNotEmpty()

    /**
     * Records chunk [local]'s [sample] (four ints) at [tick], unless it is not later than the last
     * one or changes nothing.
     */
    @Synchronized
    fun append(local: Int, tick: Long, sample: IntArray) {
        val times = ticks[local]
        if (times != null) {
            if (times.last() >= tick) return
            val values = samples[local]!!
            if ((0 until SAMPLE).all { values[values.size - SAMPLE + it] == sample[it] }) return
        }
        add(local, tick, sample)
        unsaved += local to ticks[local]!!.size - 1
    }

    /**
     * Writes [into] at [offset] the sample of [local] as of [tick]; false if it did not exist yet.
     */
    @Synchronized
    fun sampleAt(local: Int, tick: Long, into: IntArray, offset: Int): Boolean {
        val index = search(local, tick) ?: return false
        samples[local]!!.copyInto(into, offset, index * SAMPLE, index * SAMPLE + SAMPLE)
        return true
    }

    /** The first chunk in Z-order positions [from] until [to] that existed at [tick], or -1. */
    @Synchronized
    fun firstPresent(from: Int, to: Int, tick: Long): Int {
        for (m in from until to) {
            val local = Morton.ORDER[m]
            val times = ticks[local] ?: continue
            if (times[0] <= tick) return local
        }
        return -1
    }

    /** Appends the samples added since the last save as one frame; returns the file's length. */
    @Synchronized
    fun save(): Long {
        if (unsaved.isEmpty()) return fileLength
        val sink = ByteSink(unsaved.size * 16)
        sink.varint(unsaved.size)
        for ((local, index) in unsaved) {
            sink.varint(local)
            sink.signed(ticks[local]!![index])
            val values = samples[local]!!
            sink.varint(values[index * SAMPLE])
            sink.signed(values[index * SAMPLE + 1].toLong())
            sink.varint(values[index * SAMPLE + 2])
            sink.signed(values[index * SAMPLE + 3].toLong())
        }
        FileChannel.open(file, CREATE, WRITE).use { channel ->
            fileLength += Frames.write(channel, fileLength, sink.toByteArray())
        }
        unsaved.clear()
        return fileLength
    }

    private fun add(local: Int, tick: Long, sample: IntArray) {
        ticks[local] = ticks[local]?.plus(tick) ?: longArrayOf(tick)
        samples[local] = samples[local]?.plus(sample) ?: sample.copyOf()
    }

    private fun search(local: Int, tick: Long): Int? {
        val times = ticks[local] ?: return null
        var low = 0
        var high = times.size - 1
        var found: Int? = null
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (times[middle] <= tick) {
                found = middle
                low = middle + 1
            } else high = middle - 1
        }
        return found
    }

    companion object {
        /** Ints per sample: block, height, depth, biome. */
        const val SAMPLE = 4

        fun fileName(key: RegionKey) = "r.${key.x}.${key.z}.ovw"

        fun load(key: RegionKey, directory: Path): OverviewRegion {
            val region = OverviewRegion(key, directory.resolve(fileName(key)))
            if (!Files.exists(region.file)) return region
            FileChannel.open(region.file, READ, WRITE).use { channel ->
                var at = 0L
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
                        val local = source.varintInt()
                        val tick = source.signed()
                        val sample =
                            intArrayOf(
                                source.varintInt(),
                                source.signed().toInt(),
                                source.varintInt(),
                                source.signed().toInt(),
                            )
                        if (region.ticks[local]?.last()?.let { it >= tick } != true)
                            region.add(local, tick, sample)
                    }
                    at += Frames.HEADER + payload.size
                }
                region.fileLength = at
            }
            return region
        }
    }
}
