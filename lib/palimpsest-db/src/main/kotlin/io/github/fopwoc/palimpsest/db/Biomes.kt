package io.github.fopwoc.palimpsest.db

/** A chunk's biomes in the layout its game version uses. */
sealed interface Biomes {
    /** One biome per column, 16 × 16, x fastest (1.7.10). */
    class Columns(val values: IntArray) : Biomes {
        init {
            require(values.size == COLUMNS) { "Expected $COLUMNS columns, got ${values.size}" }
        }

        fun at(x: Int, z: Int): BiomeId = BiomeId(values[(z shl 4) or x])

        companion object {
            const val COLUMNS = 16 * 16
        }
    }
}
