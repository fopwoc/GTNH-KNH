package io.github.fopwoc.mods.palimpsest.tree

/**
 * Encodes one channel's values — a whole 16×16 grid or the covered pixels of a delta — picking the
 * smallest of a few shapes, so a tile pays for how much it varies and no more:
 *
 * - `SOLID`: one value for every pixel.
 * - `PALETTE`: the distinct values, then per-pixel indices at exactly `ceil(log2 n)` bits.
 * - `PREDICTED` (full grids only): each value as a residual against the median predictor of its
 *   west, north and north-west neighbours (LOCO-I / JPEG-LS), residuals bit-packed at the width the
 *   largest one needs.
 * - `PALETTE_CODED` / `PREDICTED_CODED`: the same indices or residuals through an adaptive range
 *   coder — indices conditioned on the western neighbour, residuals on the local gradient — so a
 *   tile that is mostly one thing with a few exceptions, or terrain that is mostly flat, costs
 *   close to its entropy. No table is stored; the model adapts identically on both sides.
 * - `RAW`: every value at its natural width, for the pathological tile.
 *
 * The chosen mode is the first byte; the choice is deterministic so identical tiles encode to
 * identical bytes, which the store relies on for deduplication.
 */
object ChannelCodec {
    private const val SOLID = 0
    private const val PALETTE = 1
    private const val PREDICTED = 2
    private const val RAW = 3
    private const val PALETTE_CODED = 4
    private const val PREDICTED_CODED = 5
    private const val SIDE = TileRecord.SIDE
    /** Residual symbols below this are coded directly; this one escapes to raw bits. */
    private const val ESCAPE = 15
    private const val GRADIENT_CONTEXTS = 4
    /** Palettes this large or smaller get one model per western neighbour. */
    private const val CONTEXTUAL_PALETTE = 32

    fun encode(sink: ByteSink, values: IntArray, width: Int) {
        require(values.isNotEmpty() && width in 1..2)
        val maxValue = (1 shl (width * 8)) - 1
        require(values.all { it in 0..maxValue })
        val distinct = values.toSortedSet().toIntArray()
        if (distinct.size == 1) {
            sink.byte(SOLID)
            sink.fixed(distinct[0].toLong(), width)
            return
        }
        // On full grids the coded shapes win all but a few in a thousand times, by a few bytes;
        // the bit-packed ones are only tried where there is nothing to adapt to.
        val candidates = ArrayList<ByteSink>(2)
        candidates += ByteSink().also { encodePaletteCoded(it, values, distinct, width) }
        if (values.size == TileRecord.PIXELS) {
            candidates += ByteSink().also { encodePredictedCoded(it, values, width) }
        } else {
            candidates += ByteSink().also { encodePalette(it, values, distinct, width) }
        }
        val rawBytes = 1 + values.size * width
        val best = candidates.minBy { it.size }
        if (best.size <= rawBytes) {
            sink.bytes(best.toByteArray())
        } else {
            sink.byte(RAW)
            for (value in values) sink.fixed(value.toLong(), width)
        }
    }

    /** Name of the mode a channel was written in; consumes it like [decode]. */
    fun inspect(source: ByteSource, count: Int, width: Int): String {
        val start = source.position
        val mode = source.byte()
        val name =
            when (mode) {
                SOLID -> "solid"
                PALETTE -> "palette"
                PALETTE_CODED -> "palette-coded"
                PREDICTED -> "predicted"
                PREDICTED_CODED -> "predicted-coded"
                RAW -> "raw"
                else -> throw CorruptTreeException("Unknown channel mode $mode")
            }
        val rewound = ByteSource(source.buffer, start, source.limit)
        decode(rewound, count, width)
        source.skip(rewound.position - source.position)
        return name
    }

    fun decode(source: ByteSource, count: Int, width: Int): IntArray {
        require(count > 0 && width in 1..2)
        return when (val mode = source.byte()) {
            SOLID -> IntArray(count).also { it.fill(source.fixed(width).toInt()) }
            PALETTE -> decodePalette(source, count, width)
            PALETTE_CODED -> decodePaletteCoded(source, count, width)
            PREDICTED -> {
                if (count != TileRecord.PIXELS)
                    throw CorruptTreeException("Predicted channel needs a full grid")
                decodePredicted(source, width)
            }
            PREDICTED_CODED -> {
                if (count != TileRecord.PIXELS)
                    throw CorruptTreeException("Predicted channel needs a full grid")
                decodePredictedCoded(source, width)
            }
            RAW -> IntArray(count) { source.fixed(width).toInt() }
            else -> throw CorruptTreeException("Unknown channel mode $mode")
        }
    }

    // ---- palette, bit-packed ----

    private fun encodePalette(sink: ByteSink, values: IntArray, distinct: IntArray, width: Int) {
        sink.byte(PALETTE)
        writeDistinct(sink, distinct, width)
        val bits = bitsFor(distinct.size)
        val writer = BitWriter(sink)
        for (value in values) writer.write(distinct.binarySearch(value), bits)
        writer.finish()
    }

    private fun decodePalette(source: ByteSource, count: Int, width: Int): IntArray {
        val distinct = readDistinct(source, width)
        val bits = bitsFor(distinct.size)
        val reader = BitReader(source)
        val values =
            IntArray(count) {
                val index = reader.read(bits)
                if (index >= distinct.size)
                    throw CorruptTreeException("Palette index $index of ${distinct.size}")
                distinct[index]
            }
        reader.finish()
        return values
    }

    // ---- palette, range coded with the western neighbour as context ----

    private fun encodePaletteCoded(
        sink: ByteSink,
        values: IntArray,
        distinct: IntArray,
        width: Int,
    ) {
        sink.byte(PALETTE_CODED)
        writeDistinct(sink, distinct, width)
        val models = paletteModels(distinct.size, values.size)
        val encoder = RangeEncoder(sink)
        var west = 0
        for ((position, value) in values.withIndex()) {
            val index = distinct.binarySearch(value)
            models[paletteContext(models.size, position, west)].encode(encoder, index)
            west = index
        }
        encoder.finish()
    }

    private fun decodePaletteCoded(source: ByteSource, count: Int, width: Int): IntArray {
        val distinct = readDistinct(source, width)
        val models = paletteModels(distinct.size, count)
        val decoder = RangeDecoder(source)
        var west = 0
        return IntArray(count) { position ->
            val index = models[paletteContext(models.size, position, west)].decode(decoder)
            west = index
            distinct[index]
        }
    }

    /**
     * One model per possible western index for small palettes on full grids; one model otherwise.
     */
    private fun paletteModels(distinct: Int, count: Int): Array<AdaptiveModel> {
        val contexts =
            if (count == TileRecord.PIXELS && distinct <= CONTEXTUAL_PALETTE) distinct else 1
        return Array(contexts) { AdaptiveModel(distinct) }
    }

    private fun paletteContext(contexts: Int, position: Int, west: Int): Int =
        if (contexts == 1 || position % SIDE == 0) 0 else west

    // ---- predicted, bit-packed ----

    private fun encodePredicted(sink: ByteSink, values: IntArray, width: Int) {
        val bits = residualBits(values)
        sink.byte(PREDICTED)
        sink.fixed(values[0].toLong(), width)
        sink.byte(bits)
        val writer = BitWriter(sink)
        for (position in 1 until TileRecord.PIXELS) {
            writer.write(zigzag(values[position] - predict(values, position)), bits)
        }
        writer.finish()
    }

    private fun decodePredicted(source: ByteSource, width: Int): IntArray {
        val values = IntArray(TileRecord.PIXELS)
        values[0] = source.fixed(width).toInt()
        val bits = source.byte()
        if (bits > 32) throw CorruptTreeException("Residual width $bits")
        val reader = BitReader(source)
        for (position in 1 until TileRecord.PIXELS) {
            values[position] = predict(values, position) + unzigzag(reader.read(bits))
        }
        reader.finish()
        return values
    }

    // ---- predicted, range coded with the local gradient as context ----

    private fun encodePredictedCoded(sink: ByteSink, values: IntArray, width: Int) {
        sink.byte(PREDICTED_CODED)
        sink.fixed(values[0].toLong(), width)
        val models = Array(GRADIENT_CONTEXTS) { AdaptiveModel(ESCAPE + 1) }
        val encoder = RangeEncoder(sink)
        val escapeBits = width * 8 + 1
        for (position in 1 until TileRecord.PIXELS) {
            val residual = zigzag(values[position] - predict(values, position))
            val model = models[gradientContext(values, position)]
            if (residual < ESCAPE) {
                model.encode(encoder, residual)
            } else {
                model.encode(encoder, ESCAPE)
                encoder.encodeBits(residual, escapeBits)
            }
        }
        encoder.finish()
    }

    private fun decodePredictedCoded(source: ByteSource, width: Int): IntArray {
        val values = IntArray(TileRecord.PIXELS)
        values[0] = source.fixed(width).toInt()
        val models = Array(GRADIENT_CONTEXTS) { AdaptiveModel(ESCAPE + 1) }
        val decoder = RangeDecoder(source)
        val escapeBits = width * 8 + 1
        for (position in 1 until TileRecord.PIXELS) {
            val model = models[gradientContext(values, position)]
            var residual = model.decode(decoder)
            if (residual == ESCAPE) residual = decoder.decodeBits(escapeBits)
            values[position] = predict(values, position) + unzigzag(residual)
        }
        return values
    }

    /** How rough the already-decoded neighbourhood is: flat, gentle, hilly, cliff. */
    private fun gradientContext(values: IntArray, position: Int): Int {
        val x = position % SIDE
        val z = position / SIDE
        if (z == 0 || x == 0) return 0
        val a = values[position - 1]
        val b = values[position - SIDE]
        val c = values[position - SIDE - 1]
        val activity = Math.abs(a - c) + Math.abs(b - c)
        return when {
            activity == 0 -> 0
            activity <= 2 -> 1
            activity <= 8 -> 2
            else -> 3
        }
    }

    // ---- shared ----

    private fun writeDistinct(sink: ByteSink, distinct: IntArray, width: Int) {
        sink.varint(distinct.size)
        for (value in distinct) sink.fixed(value.toLong(), width)
    }

    private fun readDistinct(source: ByteSource, width: Int): IntArray {
        val size = source.varintInt()
        if (size !in 2..AdaptiveModel.MAX_ALPHABET)
            throw CorruptTreeException("Palette with $size entries")
        return IntArray(size) { source.fixed(width).toInt() }
    }

    /**
     * Median edge detector over west (a), north (b) and north-west (c); edges fall back to a or b.
     */
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

    private fun residualBits(values: IntArray): Int {
        var widest = 0
        for (position in 1 until TileRecord.PIXELS) {
            widest =
                maxOf(
                    widest,
                    32 -
                        Integer.numberOfLeadingZeros(
                            zigzag(values[position] - predict(values, position))
                        ),
                )
        }
        return widest
    }

    private fun zigzag(value: Int): Int = (value shl 1) xor (value shr 31)

    private fun unzigzag(value: Int): Int = (value ushr 1) xor -(value and 1)

    private fun bitsFor(distinct: Int): Int = 32 - Integer.numberOfLeadingZeros(distinct - 1)
}
