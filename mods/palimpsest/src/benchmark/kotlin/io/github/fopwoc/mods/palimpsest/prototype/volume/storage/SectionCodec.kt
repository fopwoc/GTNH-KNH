package io.github.fopwoc.mods.palimpsest.prototype.volume.storage

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.ChunkVolume
import io.github.fopwoc.mods.palimpsest.tree.AdaptiveModel
import io.github.fopwoc.mods.palimpsest.tree.ByteSink
import io.github.fopwoc.mods.palimpsest.tree.ByteSource
import io.github.fopwoc.mods.palimpsest.tree.RangeDecoder
import io.github.fopwoc.mods.palimpsest.tree.RangeEncoder
import java.util.zip.Deflater

/**
 * One 16³ section as self-contained bytes: the sorted palette of vocabulary ids, then each block's
 * palette index through the production range coder. Terrain is predictable from its neighbours,
 * so each block first answers "same as below?", then "same as west?", then "same as north?", and
 * only a block matching none of them is coded as a literal, conditioned on the block below when the
 * palette is small. Encoding is deterministic, so equal sections give equal bytes.
 */
object SectionCodec {
    fun encode(blocks: IntArray): ByteArray {
        require(blocks.size == ChunkVolume.SECTION_BLOCKS)
        val palette = blocks.distinct().sorted().toIntArray()
        val sink = ByteSink(512)
        sink.varint(palette.size)
        var previous = 0
        for (id in palette) {
            sink.varint(id - previous)
            previous = id
        }
        if (palette.size == 1) return sink.toByteArray()
        val slot = HashMap<Int, Int>(palette.size * 2).apply { palette.forEachIndexed { i, id -> put(id, i) } }
        val indices = IntArray(blocks.size) { slot.getValue(blocks[it]) }
        if (palette.size > AdaptiveModel.MAX_ALPHABET) {
            for (index in indices) sink.fixed(index.toLong(), 2)
            return sink.toByteArray()
        }
        val encoder = RangeEncoder(sink)
        Models(palette.size).walk(indices) { model, symbol ->
            model.encode(encoder, symbol)
            symbol
        }
        encoder.finish()
        return sink.toByteArray()
    }

    fun decode(bytes: ByteArray): IntArray {
        val source = ByteSource(bytes)
        var previous = 0
        val palette = IntArray(source.varintInt()) { (previous + source.varintInt()).also { previous = it } }
        if (palette.size == 1) return IntArray(ChunkVolume.SECTION_BLOCKS) { palette[0] }
        val indices =
            if (palette.size > AdaptiveModel.MAX_ALPHABET)
                IntArray(ChunkVolume.SECTION_BLOCKS) { source.fixed(2).toInt() }
            else {
                val decoder = RangeDecoder(source)
                IntArray(ChunkVolume.SECTION_BLOCKS).also { out ->
                    Models(palette.size).walk(out) { model, _ -> model.decode(decoder) }
                }
            }
        return IntArray(indices.size) { palette[indices[it]] }
    }

    /** Palette and bit-packed indices through Deflate: the generic-compressor reference. */
    fun deflatedSize(blocks: IntArray): Int {
        val palette = blocks.distinct().sorted()
        val slot = palette.withIndex().associate { (i, id) -> id to i }
        val bits = if (palette.size == 1) 0 else 32 - Integer.numberOfLeadingZeros(palette.size - 1)
        val sink = ByteSink(4096)
        sink.varint(palette.size)
        palette.forEach { sink.varint(it) }
        var acc = 0L
        var held = 0
        for (id in blocks) {
            acc = acc or (slot.getValue(id).toLong() shl held)
            held += bits
            while (held >= 8) {
                sink.byte((acc and 0xFF).toInt())
                acc = acc ushr 8
                held -= 8
            }
        }
        if (held > 0) sink.byte(acc.toInt())
        val input = sink.toByteArray()
        val deflater = Deflater(9)
        deflater.setInput(input)
        deflater.finish()
        val out = ByteArray(input.size + 64)
        var size = 0
        while (!deflater.finished()) size += deflater.deflate(out)
        deflater.end()
        return size
    }

    /**
     * The coding decisions shared by both directions. [step] receives a model and the symbol to
     * encode (ignored when decoding) and returns the symbol coded; the walk fills [indices] in
     * decode order, which is the order the encoder reads them in.
     */
    private class Models(private val size: Int) {
        private val sameBelow = Array(3) { AdaptiveModel(2) }
        private val sameWest = Array(3) { AdaptiveModel(2) }
        private val sameNorth = Array(3) { AdaptiveModel(2) }
        private val literal =
            if (size <= CONTEXTUAL) Array(size + 1) { AdaptiveModel(size) } else arrayOf(AdaptiveModel(size))

        fun walk(indices: IntArray, step: (AdaptiveModel, Int) -> Int) {
            for (y in 0 until 16) for (z in 0 until 16) for (x in 0 until 16) {
                val at = (y shl 8) or (z shl 4) or x
                val below = if (y > 0) indices[at - 256] else -1
                val west = if (x > 0) indices[at - 1] else -1
                val north = if (z > 0) indices[at - 16] else -1
                val agreement = when {
                    west < 0 || north < 0 -> 2
                    west == north -> 1
                    else -> 0
                }
                val target = indices[at]
                if (below >= 0 && step(sameBelow[agreement], if (target == below) 1 else 0) == 1) {
                    indices[at] = below
                    continue
                }
                if (west >= 0 && west != below &&
                    step(sameWest[agreement], if (target == west) 1 else 0) == 1) {
                    indices[at] = west
                    continue
                }
                if (north >= 0 && north != below && north != west &&
                    step(sameNorth[agreement], if (target == north) 1 else 0) == 1) {
                    indices[at] = north
                    continue
                }
                val context = if (size <= CONTEXTUAL) below + 1 else 0
                indices[at] = step(literal[context], target)
            }
        }
    }

    private const val CONTEXTUAL = 24
}
