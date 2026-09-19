package io.github.fopwoc.mods.palimpsest.analyze

import io.github.fopwoc.mods.palimpsest.tree.AdaptiveModel
import io.github.fopwoc.mods.palimpsest.tree.ByteSink
import io.github.fopwoc.mods.palimpsest.tree.ByteSource
import io.github.fopwoc.mods.palimpsest.tree.RangeEncoder
import io.github.fopwoc.mods.palimpsest.tree.Ref
import io.github.fopwoc.mods.palimpsest.tree.RefCoder
import io.github.fopwoc.mods.palimpsest.tree.SegmentFormat
import io.github.fopwoc.mods.palimpsest.tree.SegmentReader
import io.github.fopwoc.mods.palimpsest.tree.TileCodec
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.name

/** Tries height and block coding variants over the full tiles of real segments; prints bytes. */
fun main(args: Array<String>) {
    val directory = Path.of(args.single())
    val tiles = ArrayList<TileRecord>()
    for (file in Files.list(directory).use { paths -> paths.filter { it.name.endsWith(".pseg") }.toList() }) {
        val buffer = FileChannel.open(file, StandardOpenOption.READ).use { it.map(FileChannel.MapMode.READ_ONLY, 0, it.size()) }
        val reader = Segment(buffer)
        reader.scan { _, record ->
            if (record.type != SegmentFormat.RecordType.TILE) return@scan
            val decoded = TileCodec.decode(record.source, Skipping(reader.baseEpoch))
            decoded.record?.let(tiles::add)
        }
    }
    println("${tiles.size} full tiles")
    val variants =
        listOf(
            "height: current (4 gradient ctx, esc 15)" to { t: TileRecord -> heights(t, escape = 15, blockContext = false, gradientContexts = 4) },
            "height: escape 31" to { t: TileRecord -> heights(t, escape = 31, blockContext = false, gradientContexts = 4) },
            "height: escape 63" to { t: TileRecord -> heights(t, escape = 63, blockContext = false, gradientContexts = 4) },
            "height: + block-change context" to { t: TileRecord -> heights(t, escape = 15, blockContext = true, gradientContexts = 4) },
            "height: escape 31 + block-change" to { t: TileRecord -> heights(t, escape = 31, blockContext = true, gradientContexts = 4) },
            "height: escape 63 + block-change + 6 grad" to { t: TileRecord -> heights(t, escape = 63, blockContext = true, gradientContexts = 6) },
            "block: current (west ctx)" to { t: TileRecord -> blocks(t, north = false) },
            "block: west+north ctx" to { t: TileRecord -> blocks(t, north = true) },
        )
    for ((name, variant) in variants) {
        val total = tiles.sumOf { variant(it) }
        println("%-42s %9d bytes  %6.1f B/tile".format(name, total, total.toDouble() / tiles.size))
    }
}

private const val SIDE = 16

private fun predict(values: IntArray, position: Int): Int {
    val x = position % SIDE
    val z = position / SIDE
    if (z == 0) return values[position - 1]
    if (x == 0) return values[position - SIDE]
    val a = values[position - 1]
    val b = values[position - SIDE]
    val c = values[position - SIDE - 1]
    return when {
        c >= maxOf(a, b) -> minOf(a, b)
        c <= minOf(a, b) -> maxOf(a, b)
        else -> a + b - c
    }
}

private fun gradient(values: IntArray, position: Int, contexts: Int): Int {
    val x = position % SIDE
    val z = position / SIDE
    if (z == 0 || x == 0) return 0
    val a = values[position - 1]
    val b = values[position - SIDE]
    val c = values[position - SIDE - 1]
    val activity = Math.abs(a - c) + Math.abs(b - c)
    return when (contexts) {
        4 -> when {
            activity == 0 -> 0
            activity <= 2 -> 1
            activity <= 8 -> 2
            else -> 3
        }
        else -> when {
            activity == 0 -> 0
            activity == 1 -> 1
            activity <= 3 -> 2
            activity <= 7 -> 3
            activity <= 15 -> 4
            else -> 5
        }
    }
}

private fun zigzag(value: Int): Int = (value shl 1) xor (value shr 31)

private fun heights(tile: TileRecord, escape: Int, blockContext: Boolean, gradientContexts: Int): Int {
    val values = tile.channel(TileRecord.Channel.HEIGHT)
    val blocks = tile.channel(TileRecord.Channel.BLOCK)
    if (values.all { it == values[0] }) return 2
    val sink = ByteSink()
    sink.byte(0)
    sink.byte(values[0])
    val contexts = gradientContexts * (if (blockContext) 3 else 1)
    val models = Array(contexts) { AdaptiveModel(escape + 1) }
    val encoder = RangeEncoder(sink)
    for (position in 1 until 256) {
        val residual = zigzag(values[position] - predict(values, position))
        var context = gradient(values, position, gradientContexts)
        if (blockContext) {
            val x = position % SIDE
            val z = position / SIDE
            val west = if (x > 0) blocks[position - 1] else -1
            val north = if (z > 0) blocks[position - SIDE] else -1
            val change = when {
                west == blocks[position] && (north == blocks[position] || north == -1) -> 0
                west == blocks[position] || north == blocks[position] -> 1
                else -> 2
            }
            context += gradientContexts * change
        }
        val model = models[context]
        if (residual < escape) model.encode(encoder, residual)
        else {
            model.encode(encoder, escape)
            encoder.encodeBits(residual, 9)
        }
    }
    encoder.finish()
    return sink.size
}

private fun blocks(tile: TileRecord, north: Boolean): Int {
    val values = tile.channel(TileRecord.Channel.BLOCK)
    val distinct = values.toSortedSet().toIntArray()
    if (distinct.size == 1) return 3
    val sink = ByteSink()
    sink.byte(0)
    sink.varint(distinct.size)
    for (value in distinct) sink.fixed(value.toLong(), 2)
    val n = distinct.size
    val contextual = n <= 32
    val models = Array(if (!contextual) 1 else if (north) 2 * n else n) { AdaptiveModel(n) }
    val encoder = RangeEncoder(sink)
    val indices = IntArray(256) { distinct.binarySearch(values[it]) }
    for (position in 0 until 256) {
        val x = position % SIDE
        val z = position / SIDE
        val west = if (x > 0) indices[position - 1] else 0
        val up = if (z > 0) indices[position - SIDE] else west
        val context =
            when {
                !contextual -> 0
                !north -> if (x == 0) 0 else west
                west == up -> west
                else -> n + up
            }
        models[context].encode(encoder, indices[position])
    }
    encoder.finish()
    return sink.size
}

private class Skipping(override val baseEpoch: Long) : RefCoder {
    override fun write(sink: ByteSink, ref: Ref) = error("read only")

    override fun read(source: ByteSource): Ref {
        val where = source.varintInt()
        if (where == 0) return Ref.NULL
        if (where >= 2) source.varintInt()
        source.varintInt()
        return Ref(0, 0)
    }
}

private class Segment(private val mapped: ByteBuffer) : SegmentReader() {
    private val header = SegmentFormat.readHeader(mapped)
    private val trailer = SegmentFormat.readTrailer(mapped)
    override val machineId: Int = header.machineId
    override val ordinal: Int = header.ordinal
    override val baseEpoch: Long = header.baseEpoch
    override val slots: IntArray = intArrayOf(machineId) + (trailer?.slots ?: IntArray(0))
    private val end = if (trailer != null) SegmentFormat.trailerStart(mapped) else mapped.limit()

    override fun view(): Pair<ByteBuffer, Int> = mapped to end
}
