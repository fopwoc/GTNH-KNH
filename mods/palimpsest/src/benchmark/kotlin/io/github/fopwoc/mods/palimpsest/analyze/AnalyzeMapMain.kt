package io.github.fopwoc.mods.palimpsest.analyze

import io.github.fopwoc.mods.palimpsest.tree.ByteSource
import io.github.fopwoc.mods.palimpsest.tree.ChannelCodec
import io.github.fopwoc.mods.palimpsest.tree.MachineId
import io.github.fopwoc.mods.palimpsest.tree.RefCoder
import io.github.fopwoc.mods.palimpsest.tree.SegmentFormat
import io.github.fopwoc.mods.palimpsest.tree.SegmentReader
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.name

/**
 * Where the bytes of a slice directory go: per record kind, per tile-record kind, and per channel
 * mode, read straight from the segment files without opening them for writing. Usage: `./gradlew -p
 * mods/palimpsest analyzeMap --args=<slice directory>`.
 */
fun main(args: Array<String>) {
    val directory = Path.of(args.singleOrNull() ?: error("Pass the slice directory (…/dimN/y255)"))
    val files =
        Files.list(directory).use { paths -> paths.filter { it.name.endsWith(".pseg") }.toList() }
    val kinds = Tally()
    val tileKinds = Tally()
    val channels = Array(TileRecord.Channel.entries.size) { Tally() }
    var tiles = 0
    for (file in files) {
        val buffer =
            FileChannel.open(file, StandardOpenOption.READ).use {
                it.map(FileChannel.MapMode.READ_ONLY, 0, it.size())
            }
        val reader = OpenSegment(buffer)
        println(
            "${file.name}: ${Files.size(file)} bytes, machine ${MachineId.hex(reader.machineId)}#${reader.ordinal}"
        )
        reader.scan { _, record ->
            val length = record.source.remaining
            kinds.add(record.type.name.lowercase(), length + 2)
            if (record.type != SegmentFormat.RecordType.TILE) return@scan
            tiles++
            val source = record.source
            val start = source.position
            val kind = source.byte()
            val refs = Relative(reader.baseEpoch)
            when (kind) {
                1 -> {
                    refs.readEpoch(source)
                    refs.read(source)
                    val headerEnd = source.position
                    tileKinds.add("full", length)
                    for ((index, channel) in TileRecord.Channel.entries.withIndex()) {
                        val start = source.position
                        val mode = ChannelCodec.inspect(source, TileRecord.PIXELS, channel.bytes)
                        channels[index].add(mode, source.position - start)
                    }
                    tileKinds.add("full-header", headerEnd - start)
                }
                2 -> tileKinds.add("delta", length)
                3 -> tileKinds.add("link", length)
                else -> tileKinds.add("unknown", length)
            }
        }
    }
    println()
    println("$tiles tile records")
    println("by record kind (with framing):")
    kinds.print()
    println("tile records by kind:")
    tileKinds.print()
    for ((index, channel) in TileRecord.Channel.entries.withIndex()) {
        println("channel ${channel.name.lowercase()} in full records, by mode:")
        channels[index].print()
    }
}

private class Tally {
    private val bytes = LinkedHashMap<String, Long>()
    private val counts = LinkedHashMap<String, Int>()

    fun add(key: String, size: Int) {
        bytes.merge(key, size.toLong(), Long::plus)
        counts.merge(key, 1, Int::plus)
    }

    fun print() {
        val total = bytes.values.sum().coerceAtLeast(1)
        for ((key, size) in bytes.entries.sortedByDescending { it.value }) {
            println(
                "  %-18s %10d bytes %5.1f%%  %8d records  %6.1f B avg"
                    .format(
                        key,
                        size,
                        100.0 * size / total,
                        counts[key],
                        size.toDouble() / counts[key]!!,
                    )
            )
        }
    }
}

/**
 * Refs are only skipped here, never resolved, so segment and slot numbers are read as plain
 * varints.
 */
private class Relative(override val baseEpoch: Long) : RefCoder {
    override fun write(
        sink: io.github.fopwoc.mods.palimpsest.tree.ByteSink,
        ref: io.github.fopwoc.mods.palimpsest.tree.Ref,
    ) = error("read only")

    override fun read(source: ByteSource): io.github.fopwoc.mods.palimpsest.tree.Ref {
        val where = source.varintInt()
        if (where == 0) return io.github.fopwoc.mods.palimpsest.tree.Ref.NULL
        if (where >= 2) source.varintInt()
        source.varintInt()
        return io.github.fopwoc.mods.palimpsest.tree.Ref(0, 0)
    }
}

/** A segment file opened read-only whether or not it was sealed. */
private class OpenSegment(private val mapped: ByteBuffer) : SegmentReader() {
    private val header = SegmentFormat.readHeader(mapped)
    private val trailer = SegmentFormat.readTrailer(mapped)
    override val machineId: Int = header.machineId
    override val ordinal: Int = header.ordinal
    override val baseEpoch: Long = header.baseEpoch
    override val slots: IntArray = intArrayOf(machineId) + (trailer?.slots ?: IntArray(0))
    private val end = if (trailer != null) SegmentFormat.trailerStart(mapped) else mapped.limit()

    override fun view(): Pair<ByteBuffer, Int> = mapped to end
}
