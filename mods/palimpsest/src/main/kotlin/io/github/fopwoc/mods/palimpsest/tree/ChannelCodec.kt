package io.github.fopwoc.mods.palimpsest.tree

/**
 * Encodes one channel's values — a whole 16×16 grid or the covered pixels of a delta — picking
 * the smallest of a few shapes, so a tile pays for how much it varies and no more:
 *
 * - `SOLID`: one value for every pixel.
 * - `PALETTE`: the distinct values, then per-pixel indices at exactly `ceil(log2 n)` bits.
 * - `PREDICTED` (full grids only): each value as a residual against the median predictor of its
 *   west, north and north-west neighbours (LOCO-I / JPEG-LS), residuals bit-packed at the width the
 *   largest one needs. Heights are smooth, so residuals are mostly 0 and ±1.
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
    private const val SIDE = TileRecord.SIDE

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
        val paletteBytes = paletteSize(values.size, distinct.size, width)
        val rawBytes = 1 + values.size * width
        val predictedBits = if (values.size == TileRecord.PIXELS) residualBits(values) else -1
        val predictedBytes = if (predictedBits < 0) Int.MAX_VALUE else predictedSize(predictedBits, width)
        when (minOf(paletteBytes, rawBytes, predictedBytes)) {
            predictedBytes -> encodePredicted(sink, values, width, predictedBits)
            paletteBytes -> encodePalette(sink, values, distinct, width)
            else -> {
                sink.byte(RAW)
                for (value in values) sink.fixed(value.toLong(), width)
            }
        }
    }

    fun decode(source: ByteSource, count: Int, width: Int): IntArray {
        require(count > 0 && width in 1..2)
        return when (val mode = source.byte()) {
            SOLID -> IntArray(count).also { it.fill(source.fixed(width).toInt()) }
            PALETTE -> decodePalette(source, count, width)
            PREDICTED -> {
                if (count != TileRecord.PIXELS) throw CorruptTreeException("Predicted channel needs a full grid")
                decodePredicted(source, width)
            }
            RAW -> IntArray(count) { source.fixed(width).toInt() }
            else -> throw CorruptTreeException("Unknown channel mode $mode")
        }
    }

    private fun paletteSize(count: Int, distinct: Int, width: Int): Int =
        1 + varintSize(distinct) + distinct * width + (count * bitsFor(distinct) + 7) / 8

    private fun predictedSize(bits: Int, width: Int): Int = 1 + width + 1 + ((TileRecord.PIXELS - 1) * bits + 7) / 8

    private fun encodePalette(sink: ByteSink, values: IntArray, distinct: IntArray, width: Int) {
        sink.byte(PALETTE)
        sink.varint(distinct.size)
        for (value in distinct) sink.fixed(value.toLong(), width)
        val bits = bitsFor(distinct.size)
        val writer = BitWriter(sink)
        for (value in values) writer.write(distinct.binarySearch(value), bits)
        writer.finish()
    }

    private fun decodePalette(source: ByteSource, count: Int, width: Int): IntArray {
        val size = source.varintInt()
        if (size < 2) throw CorruptTreeException("Palette with $size entries")
        val distinct = IntArray(size) { source.fixed(width).toInt() }
        val bits = bitsFor(size)
        val reader = BitReader(source)
        val values =
            IntArray(count) {
                val index = reader.read(bits)
                if (index >= size) throw CorruptTreeException("Palette index $index of $size")
                distinct[index]
            }
        reader.finish()
        return values
    }

    private fun encodePredicted(sink: ByteSink, values: IntArray, width: Int, bits: Int) {
        sink.byte(PREDICTED)
        sink.fixed(values[0].toLong(), width)
        sink.byte(bits)
        val writer = BitWriter(sink)
        for (position in 1 until TileRecord.PIXELS) {
            val residual = values[position] - predict(values, position)
            writer.write(zigzag(residual), bits)
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

    /** Median edge detector over west (a), north (b) and north-west (c); edges fall back to a or b. */
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
            widest = maxOf(widest, 32 - Integer.numberOfLeadingZeros(zigzag(values[position] - predict(values, position))))
        }
        return widest
    }

    private fun zigzag(value: Int): Int = (value shl 1) xor (value shr 31)

    private fun unzigzag(value: Int): Int = (value ushr 1) xor -(value and 1)

    private fun bitsFor(distinct: Int): Int = 32 - Integer.numberOfLeadingZeros(distinct - 1)

    private fun varintSize(value: Int): Int = if (value < 0x80) 1 else if (value < 0x4000) 2 else 3
}
