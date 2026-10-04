package io.github.fopwoc.palimpsest.db.codec

import io.github.fopwoc.palimpsest.db.SectionBlocks

/**
 * A section as the changes from an earlier version of itself: the changed positions as a bitmap
 * through the range coder (a changed block tends to sit next to another), then each changed block's
 * new id through a palette of the new ids. A torch costs about 10 bytes where the whole section
 * costs about 400; even a quarter of the section replaced costs half of it.
 */
internal object SectionDelta {
    /** The delta from [before] to [after], or null when a full section is the better choice. */
    fun encode(before: IntArray, after: IntArray): ByteArray? {
        require(before.size == SectionBlocks.VOLUME && after.size == SectionBlocks.VOLUME)
        var changed = 0
        for (at in 0 until SectionBlocks.VOLUME) if (before[at] != after[at]) changed++
        if (changed == 0 || changed > MAX_CHANGED) return null
        val palette =
            IntArray(changed)
                .also { ids ->
                    var n = 0
                    for (at in 0 until SectionBlocks.VOLUME) if (before[at] != after[at])
                        ids[n++] = after[at]
                }
                .distinct()
                .sorted()
                .toIntArray()
        if (palette.size > AdaptiveModel.MAX_ALPHABET) return null
        val sink = ByteSink(64)
        sink.varint(palette.size)
        var previous = 0
        for (id in palette) {
            sink.varint(id - previous)
            previous = id
        }
        val slot =
            HashMap<Int, Int>(palette.size * 2).apply {
                palette.forEachIndexed { i, id -> put(id, i) }
            }
        val encoder = RangeEncoder(sink)
        walk(palette.size) { bits, values, at, wasChanged ->
            val isChanged = before[at] != after[at]
            bits[if (wasChanged) 1 else 0].encode(encoder, if (isChanged) 1 else 0)
            if (isChanged) values?.encode(encoder, slot.getValue(after[at]))
            isChanged
        }
        encoder.finish()
        return sink.toByteArray()
    }

    /** [base] with the changes in [bytes] applied; [base] itself is left alone. */
    fun apply(base: IntArray, bytes: ByteArray): IntArray {
        val source = ByteSource(bytes)
        var previous = 0
        val palette =
            IntArray(source.varintInt()) { (previous + source.varintInt()).also { previous = it } }
        if (palette.isEmpty() || palette.size > AdaptiveModel.MAX_ALPHABET)
            throw CorruptDataException("Bad delta palette")
        val decoder = RangeDecoder(source)
        val result = base.copyOf()
        walk(palette.size) { bits, values, at, wasChanged ->
            val isChanged = bits[if (wasChanged) 1 else 0].decode(decoder) == 1
            if (isChanged) result[at] = palette[values?.decode(decoder) ?: 0]
            isChanged
        }
        return result
    }

    /** The shared walk: [step] codes position `at` given whether the one before changed. */
    private inline fun walk(
        paletteSize: Int,
        step: (Array<AdaptiveModel>, AdaptiveModel?, Int, Boolean) -> Boolean,
    ) {
        val bits = Array(2) { AdaptiveModel(2) }
        val values = if (paletteSize > 1) AdaptiveModel(paletteSize) else null
        var wasChanged = false
        for (at in 0 until SectionBlocks.VOLUME) wasChanged = step(bits, values, at, wasChanged)
    }

    /** Past half the section, a full one is never larger. */
    private const val MAX_CHANGED = SectionBlocks.VOLUME / 2
}
