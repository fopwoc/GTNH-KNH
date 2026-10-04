package io.github.fopwoc.palimpsest.db.codec

/**
 * 128-bit identity of stored content. Equal hashes are treated as equal content, so the width is
 * what keeps deduplication from ever merging two different sections. Every value feeds one lane of
 * each 64-bit half, four lanes per half, so the multiply chains run side by side instead of one
 * after another; the lanes fold together at the end.
 */
internal data class ContentHash(val high: Long, val low: Long) {
    companion object {
        /** [kind] separates content types whose values could coincide, like sections and biomes. */
        fun of(values: IntArray, kind: Int): ContentHash {
            var h0 = SEED_HIGH + kind
            var h1 = h0 xor LANE_1
            var h2 = h0 xor LANE_2
            var h3 = h0 xor LANE_3
            var l0 = SEED_LOW - kind
            var l1 = l0 xor LANE_1
            var l2 = l0 xor LANE_2
            var l3 = l0 xor LANE_3
            val end = values.size and 3.inv()
            var at = 0
            while (at < end) {
                val v0 = values[at].toLong()
                val v1 = values[at + 1].toLong()
                val v2 = values[at + 2].toLong()
                val v3 = values[at + 3].toLong()
                h0 = step(h0, v0, HIGH)
                h1 = step(h1, v1, HIGH)
                h2 = step(h2, v2, HIGH)
                h3 = step(h3, v3, HIGH)
                l0 = step(l0, v0, LOW)
                l1 = step(l1, v1, LOW)
                l2 = step(l2, v2, LOW)
                l3 = step(l3, v3, LOW)
                at += 4
            }
            while (at < values.size) {
                h0 = step(h0, values[at].toLong(), HIGH)
                l0 = step(l0, values[at].toLong(), LOW)
                at++
            }
            return ContentHash(
                fold(h0, h1, h2, h3, values.size, HIGH),
                fold(l0, l1, l2, l3, values.size, LOW),
            )
        }

        private fun step(hash: Long, value: Long, multiplier: Long): Long {
            val mixed = (hash xor value) * multiplier
            return mixed xor (mixed ushr 29)
        }

        private fun fold(a: Long, b: Long, c: Long, d: Long, size: Int, multiplier: Long): Long {
            val folded =
                step(
                    step(step(step(a, b, multiplier), c, multiplier), d, multiplier),
                    size.toLong(),
                    multiplier,
                )
            return folded xor (folded ushr 32)
        }

        private const val SEED_HIGH = 0x2545F4914F6CDD1DL
        private const val SEED_LOW = -0x61c8864680b583ebL
        private const val HIGH = -0x40a7b892e31b1a47L
        private const val LOW = -0x7a5a1fb3a2f2e2c9L
        private const val LANE_1 = 0x6A09E667F3BCC908L
        private const val LANE_2 = -0x4498517a7b3558c5L
        private const val LANE_3 = 0x3C6EF372FE94F82BL
    }
}
