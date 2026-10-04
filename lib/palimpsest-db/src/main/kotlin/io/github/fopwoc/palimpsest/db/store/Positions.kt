package io.github.fopwoc.palimpsest.db.store

/**
 * A stored blob's place as one long: segment ordinal in the high 24 bits, file offset in the low 40
 * (1 TiB per segment). [AIR] marks a slot with no blob.
 */
internal object Positions {
    const val AIR = -1L
    private const val OFFSET_BITS = 40
    private const val OFFSET_MASK = (1L shl OFFSET_BITS) - 1

    fun of(segment: Int, offset: Long): Long {
        require(segment >= 0 && offset in 0..OFFSET_MASK) {
            "Blob position out of range: $segment/$offset"
        }
        return (segment.toLong() shl OFFSET_BITS) or offset
    }

    fun segment(position: Long): Int = (position ushr OFFSET_BITS).toInt()

    fun offset(position: Long): Long = position and OFFSET_MASK
}
