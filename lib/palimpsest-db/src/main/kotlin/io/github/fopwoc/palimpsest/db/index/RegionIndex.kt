package io.github.fopwoc.palimpsest.db.index

import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.ContentHash
import io.github.fopwoc.palimpsest.db.store.Frames
import io.github.fopwoc.palimpsest.db.store.Positions
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE

/**
 * Every version of the chunks in one region with its encoded surface, in memory and in an
 * append-only file of frames. Each entry is a patch against the chunk's previous entry: the blob
 * position, length and content hash of every changed slot, and the surface only when it changed,
 * since most edits happen out of sight. Appending is idempotent by tick, so replaying history that
 * is already indexed changes nothing. Thread-safe; versions and surfaces handed out are never
 * modified.
 */
internal class RegionIndex private constructor(val key: RegionKey, private val file: Path) {
    private class Entry(
        val local: Int,
        val version: LongArray,
        val mask: Long,
        val surface: ByteArray?,
    )

    private val versions = arrayOfNulls<Array<LongArray>>(RegionKey.CHUNKS)
    private val surfaces = arrayOfNulls<Array<ByteArray>>(RegionKey.CHUNKS)
    private val unsaved = ArrayList<Entry>()

    /** Every delta in the region: its position to its base's position and length. */
    private val bases = HashMap<Long, LongArray>()
    private var fileLength = 0L

    /** Whether the commit pipeline has seen this region since it was loaded. */
    @Volatile var seenByWriter = false

    @get:Synchronized
    val dirty: Boolean
        get() = unsaved.isNotEmpty()

    @Synchronized fun latest(local: Int): LongArray? = versions[local]?.last()

    /** The base of the delta at [position] as (position, length), or null for a full blob. */
    @Synchronized fun baseOf(position: Long): LongArray? = bases[position]

    /** Deltas between [position] and the full section its chain ends in. */
    @Synchronized
    fun depth(position: Long): Int {
        var depth = 0
        var at = position
        while (true) at = bases[at]?.get(0)?.also { depth++ } ?: return depth
    }

    /** Every chunk that has versions: its local, its versions oldest first and their surfaces. */
    @Synchronized
    fun chunks(): List<Triple<Int, List<LongArray>, List<ByteArray>>> =
        (0 until RegionKey.CHUNKS).mapNotNull { local ->
            versions[local]?.let { Triple(local, it.toList(), surfaces[local]!!.toList()) }
        }

    /**
     * Replaces everything, in memory and on disk, with [entries] (local, version, surface; each
     * chunk's in tick order), written as one fresh frame. For following history that moved.
     */
    @Synchronized
    fun replace(entries: List<Triple<Int, LongArray, ByteArray>>) {
        versions.fill(null)
        surfaces.fill(null)
        bases.clear()
        unsaved.clear()
        for ((local, version, surface) in entries) append(local, version, surface)
        Files.deleteIfExists(file)
        fileLength = 0
        save()
    }

    /** The latest version of every chunk the region holds. */
    @Synchronized fun latest(): List<LongArray> = versions.mapNotNull { it?.last() }

    /** The last version at or before [tick]. */
    @Synchronized
    fun at(local: Int, tick: Long): LongArray? = search(local, tick)?.let { versions[local]!![it] }

    /** Locals of the chunks with a version after [from] up to and including [to]. */
    @Synchronized
    fun changedBetween(from: Long, to: Long): List<Int> =
        (0 until RegionKey.CHUNKS).filter { local ->
            versions[local]?.any {
                val tick = Versions.tick(it)
                tick > from && tick <= to
            } == true
        }

    /** The encoded surface of the last version at or before [tick]. */
    @Synchronized
    fun surfaceAt(local: Int, tick: Long): ByteArray? =
        search(local, tick)?.let { surfaces[local]!![it] }

    /** Adds [version] with its [surface] unless the chunk already has one at or after its tick. */
    @Synchronized
    fun append(local: Int, version: LongArray, surface: ByteArray): Boolean {
        val previous = versions[local]?.last()
        if (previous != null && Versions.tick(previous) >= Versions.tick(version)) return false
        val previousSurface = surfaces[local]?.last()
        val changed = previousSurface == null || !previousSurface.contentEquals(surface)
        val kept = if (changed) surface else previousSurface!!
        unsaved += Entry(local, version, mask(previous, version), if (changed) surface else null)
        add(local, version, kept)
        return true
    }

    /** Appends the versions added since the last save as one frame; returns the file's length. */
    @Synchronized
    fun save(): Long {
        if (unsaved.isEmpty()) return fileLength
        val sink = ByteSink(unsaved.size * 128)
        sink.varint(unsaved.size)
        for (entry in unsaved) {
            val version = entry.version
            sink.varint(entry.local)
            sink.signed(Versions.tick(version))
            sink.signed(Versions.minSection(version).toLong())
            sink.varint(Versions.slots(version))
            sink.varint(entry.mask)
            for (slot in 0 until Versions.slots(version)) {
                if (entry.mask and (1L shl slot) == 0L) continue
                val hash = Versions.hash(version, slot)
                if (hash == null) {
                    sink.varint(0)
                    continue
                }
                sink.varint(Versions.position(version, slot) + 1)
                sink.varint(Versions.length(version, slot))
                sink.fixed(hash.high, 8)
                sink.fixed(hash.low, 8)
                val base = Versions.basePosition(version, slot)
                sink.varint(base + 1)
                if (base != Positions.AIR) sink.varint(Versions.baseLength(version, slot))
            }
            val surface = entry.surface
            sink.varint(if (surface == null) 0 else surface.size + 1)
            surface?.let(sink::bytes)
        }
        FileChannel.open(file, CREATE, WRITE).use { channel ->
            fileLength += Frames.write(channel, fileLength, sink.toByteArray())
        }
        unsaved.clear()
        return fileLength
    }

    private fun add(local: Int, version: LongArray, surface: ByteArray) {
        for (slot in 0 until Versions.slots(version)) {
            val base = Versions.basePosition(version, slot)
            if (base != Positions.AIR)
                bases[Versions.position(version, slot)] =
                    longArrayOf(base, Versions.baseLength(version, slot).toLong())
        }
        versions[local] = versions[local]?.plus(version) ?: arrayOf(version)
        surfaces[local] = surfaces[local]?.plus(surface) ?: arrayOf(surface)
    }

    /** Index of the last version of [local] at or before [tick]. */
    private fun search(local: Int, tick: Long): Int? {
        val all = versions[local] ?: return null
        var low = 0
        var high = all.size - 1
        var found: Int? = null
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (Versions.tick(all[middle]) <= tick) {
                found = middle
                low = middle + 1
            } else high = middle - 1
        }
        return found
    }

    private fun load(source: ByteSource) {
        repeat(source.varintInt()) {
            val local = source.varintInt()
            val tick = source.signed()
            val minSection = source.signed().toInt()
            val slots = source.varintInt()
            val mask = source.varint()
            val previous = versions[local]?.last()?.takeIf { Versions.slots(it) == slots }
            val version = Versions.empty(tick, minSection, slots)
            for (slot in 0 until slots) {
                if (mask and (1L shl slot) == 0L) {
                    previous?.let { Versions.copy(it, version, slot) }
                    continue
                }
                val position = source.varint() - 1
                if (position == Positions.AIR) continue
                val length = source.varintInt()
                val hash = ContentHash(source.fixed(8), source.fixed(8))
                val base = source.varint() - 1
                Versions.set(
                    version,
                    slot,
                    position,
                    length,
                    hash,
                    base,
                    if (base == Positions.AIR) 0 else source.varintInt(),
                )
            }
            val surfaceLength = source.varintInt()
            val surface =
                if (surfaceLength == 0) surfaces[local]?.last() ?: ByteArray(0)
                else source.bytes(surfaceLength - 1)
            add(local, version, surface)
        }
    }

    private fun mask(previous: LongArray?, version: LongArray): Long {
        val slots = Versions.slots(version)
        if (previous == null || Versions.slots(previous) != slots) return (1L shl slots) - 1
        var mask = 0L
        for (slot in 0 until slots) if (!Versions.sameSlot(previous, version, slot))
            mask = mask or (1L shl slot)
        return mask
    }

    companion object {
        /**
         * The region as its file holds it. A torn or damaged tail, left by a crash after the last
         * index flush, is cut: the replay of history restores what it held.
         */
        fun load(key: RegionKey, directory: Path): RegionIndex {
            val region = RegionIndex(key, directory.resolve(key.fileName))
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
                    region.load(ByteSource(payload))
                    at += Frames.HEADER + payload.size
                }
                region.fileLength = at
            }
            return region
        }
    }
}
