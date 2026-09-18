package io.github.fopwoc.mods.palimpsest.storage

/**
 * One observation: covered cells replace older colors, including when all 256 cells are covered.
 */
class TileLayer(
    key: TileKey,
    epoch: Long,
    coverage: LongArray,
    colors: ByteArray,
    val isSnapshot: Boolean = false,
) {
    val key = key
    val epoch = epoch
    val coverage = coverage.copyOf()
    val colors = colors.copyOf()

    init {
        require(epoch >= 0)
        require(this.coverage.size == MASK_WORDS)
        require(this.coverage.sumOf(java.lang.Long::bitCount) == this.colors.size)
        require(this.colors.isNotEmpty())
        require(!isSnapshot || this.coverage.all { it == -1L })
    }

    companion object {
        const val SIDE = 16
        const val PIXELS = SIDE * SIDE
        const val MASK_WORDS = PIXELS / Long.SIZE_BITS

        fun full(key: TileKey, epoch: Long, colors: ByteArray): TileLayer {
            require(colors.size == PIXELS)
            return TileLayer(key, epoch, LongArray(MASK_WORDS) { -1L }, colors)
        }

        fun snapshot(key: TileKey, epoch: Long, colors: ByteArray): TileLayer {
            require(colors.size == PIXELS)
            return TileLayer(key, epoch, LongArray(MASK_WORDS) { -1L }, colors, isSnapshot = true)
        }

        fun changed(key: TileKey, epoch: Long, previous: ByteArray, next: ByteArray): TileLayer? {
            require(previous.size == PIXELS && next.size == PIXELS)
            val coverage = LongArray(MASK_WORDS)
            var count = 0
            for (position in 0 until PIXELS) {
                if (previous[position] == next[position]) continue
                coverage[position ushr 6] = coverage[position ushr 6] or (1L shl (position and 63))
                count++
            }
            if (count == 0) return null
            val colors = ByteArray(count)
            var value = 0
            for (position in 0 until PIXELS) {
                if (coverage[position ushr 6] and (1L shl (position and 63)) != 0L) {
                    colors[value++] = next[position]
                }
            }
            return TileLayer(key, epoch, coverage, colors)
        }
    }
}
