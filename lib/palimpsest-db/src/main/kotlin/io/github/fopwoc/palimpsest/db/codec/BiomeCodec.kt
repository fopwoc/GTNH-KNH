package io.github.fopwoc.palimpsest.db.codec

import io.github.fopwoc.palimpsest.db.Biomes

/**
 * Column biomes: the sorted palette, then each column as "same as west?", "same as north?" or a
 * literal palette index through the range coder. Biomes come in large patches, so most columns cost
 * a fraction of a bit.
 */
internal object BiomeCodec {
    fun encode(values: IntArray, sink: ByteSink) {
        val palette = values.distinct().sorted().toIntArray()
        sink.varint(palette.size)
        var previous = 0
        for (id in palette) {
            sink.signed((id - previous).toLong())
            previous = id
        }
        if (palette.size == 1) return
        val slot = palette.withIndex().associate { (i, id) -> id to i }
        val indices = IntArray(values.size) { slot.getValue(values[it]) }
        val encoder = RangeEncoder(sink)
        walk(indices, palette.size) { model, symbol ->
            model.encode(encoder, symbol)
            symbol
        }
        encoder.finish()
    }

    fun decode(source: ByteSource): IntArray {
        var previous = 0
        val palette =
            IntArray(source.varintInt()) {
                (previous + source.signed().toInt()).also { previous = it }
            }
        if (palette.isEmpty() || palette.size > AdaptiveModel.MAX_ALPHABET)
            throw CorruptDataException("Bad biome palette")
        if (palette.size == 1) return IntArray(Biomes.Columns.COLUMNS) { palette[0] }
        val decoder = RangeDecoder(source)
        val indices = IntArray(Biomes.Columns.COLUMNS)
        walk(indices, palette.size) { model, _ -> model.decode(decoder) }
        return IntArray(indices.size) { palette[indices[it]] }
    }

    private fun walk(indices: IntArray, size: Int, step: (AdaptiveModel, Int) -> Int) {
        val sameWest = AdaptiveModel(2)
        val sameNorth = AdaptiveModel(2)
        val literal = AdaptiveModel(size)
        for (at in indices.indices) {
            val west = if (at and 15 > 0) indices[at - 1] else -1
            val north = if (at shr 4 > 0) indices[at - 16] else -1
            val target = indices[at]
            indices[at] =
                when {
                    west >= 0 && step(sameWest, if (target == west) 1 else 0) == 1 -> west
                    north >= 0 &&
                        north != west &&
                        step(sameNorth, if (target == north) 1 else 0) == 1 -> north
                    else -> step(literal, target)
                }
        }
    }
}
