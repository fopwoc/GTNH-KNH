package io.github.fopwoc.palimpsest.db.codec

import io.github.fopwoc.palimpsest.db.SectionBlocks
import io.github.fopwoc.palimpsest.db.utils.IntIntMap

/**
 * One 16³ section as self-contained bytes: the sorted palette of block ids, then each block's
 * palette index through the range coder. Terrain is predictable from its neighbours, so each block
 * first answers "same as below?", then "same as west?", then "same as north?", and only a block
 * matching none of them is coded as a literal, conditioned on the block below when the palette is
 * small. Encoding is deterministic, so equal sections give equal bytes.
 */
internal object SectionCodec {
    fun encode(blocks: IntArray, sink: ByteSink) {
        require(blocks.size == SectionBlocks.VOLUME)
        val palette = sortedPalette(blocks)
        sink.varint(palette.size)
        var previous = 0
        for (id in palette) {
            sink.varint(id - previous)
            previous = id
        }
        if (palette.size == 1) return
        val slot = IntIntMap(palette.size).apply { palette.forEachIndexed { i, id -> put(id, i) } }
        val indices = IntArray(blocks.size) { slot.get(blocks[it]) }
        if (palette.size > AdaptiveModel.MAX_ALPHABET) {
            for (index in indices) sink.fixed(index.toLong(), 2)
            return
        }
        val encoder = RangeEncoder(sink)
        Models(palette.size).walk(indices) { model, symbol ->
            model.encode(encoder, symbol)
            symbol
        }
        encoder.finish()
    }

    fun decode(source: ByteSource): IntArray {
        var previous = 0
        val palette =
            IntArray(source.varintInt()) { (previous + source.varintInt()).also { previous = it } }
        if (palette.isEmpty()) throw CorruptDataException("Section without a palette")
        if (palette.size == 1) return IntArray(SectionBlocks.VOLUME) { palette[0] }
        val indices =
            if (palette.size > AdaptiveModel.MAX_ALPHABET)
                IntArray(SectionBlocks.VOLUME) { source.fixed(2).toInt() }
            else {
                val decoder = RangeDecoder(source)
                IntArray(SectionBlocks.VOLUME).also { out ->
                    Models(palette.size).walk(out) { model, _ -> model.decode(decoder) }
                }
            }
        return IntArray(indices.size) {
            palette.getOrNull(indices[it])
                ?: throw CorruptDataException("Palette index out of range")
        }
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
            if (size <= CONTEXTUAL) Array(size + 1) { AdaptiveModel(size) }
            else arrayOf(AdaptiveModel(size))

        fun walk(indices: IntArray, step: (AdaptiveModel, Int) -> Int) {
            for (at in 0 until SectionBlocks.VOLUME) indices[at] = code(indices, at, step)
        }

        private fun code(indices: IntArray, at: Int, step: (AdaptiveModel, Int) -> Int): Int {
            val below = if (at shr 8 > 0) indices[at - 256] else -1
            val west = if (at and 15 > 0) indices[at - 1] else -1
            val north = if ((at shr 4) and 15 > 0) indices[at - 16] else -1
            val agreement =
                when {
                    west < 0 || north < 0 -> 2
                    west == north -> 1
                    else -> 0
                }
            val target = indices[at]
            return when {
                predicts(sameBelow[agreement], below, target, step) -> below
                west != below && predicts(sameWest[agreement], west, target, step) -> west
                north != below &&
                    north != west &&
                    predicts(sameNorth[agreement], north, target, step) -> north
                else -> step(literal[if (size <= CONTEXTUAL) below + 1 else 0], target)
            }
        }

        /** Codes "is it [candidate]?" when there is a candidate at all. */
        private fun predicts(
            model: AdaptiveModel,
            candidate: Int,
            target: Int,
            step: (AdaptiveModel, Int) -> Int,
        ) = candidate >= 0 && step(model, if (target == candidate) 1 else 0) == 1
    }

    /** The distinct values of [blocks], ascending, without boxing. */
    private fun sortedPalette(blocks: IntArray): IntArray {
        val sorted = blocks.copyOf().also { it.sort() }
        var size = 0
        for (at in sorted.indices) if (at == 0 || sorted[at] != sorted[at - 1])
            sorted[size++] = sorted[at]
        return sorted.copyOf(size)
    }

    private const val CONTEXTUAL = 24
}
