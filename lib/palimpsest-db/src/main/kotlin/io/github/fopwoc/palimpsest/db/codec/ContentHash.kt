package io.github.fopwoc.palimpsest.db.codec

/**
 * 128-bit identity of stored content: two independent 64-bit mixes. Equal hashes are treated as
 * equal content, so the width is what keeps deduplication from ever merging two different sections.
 */
internal data class ContentHash(val high: Long, val low: Long) {
    companion object {
        /** [kind] separates content types whose values could coincide, like sections and biomes. */
        fun of(values: IntArray, kind: Int): ContentHash =
            ContentHash(mix(values, SEED_HIGH + kind), mix(values, SEED_LOW - kind))

        private fun mix(values: IntArray, seed: Long): Long {
            var hash = seed
            for (value in values) {
                hash = (hash xor value.toLong()) * MULTIPLIER
                hash = hash xor (hash ushr 29)
            }
            hash = (hash xor values.size.toLong()) * MULTIPLIER
            return hash xor (hash ushr 32)
        }

        private const val SEED_HIGH = 0x2545F4914F6CDD1DL
        private const val SEED_LOW = -0x61c8864680b583ebL
        private const val MULTIPLIER = -0x40a7b892e31b1a47L
    }
}
