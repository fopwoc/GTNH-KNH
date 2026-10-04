package io.github.fopwoc.palimpsest.db.store

internal enum class BlobKind {
    SECTION,
    BIOMES,

    /** A whole chunk seen from above: the truth of a surface-only dimension. */
    SURFACE;

    companion object {
        /**
         * The kind follows from the slot alone: a surface-only chunk has its surface as the one
         * slot; a full one runs sections bottom-up, then the biomes.
         */
        fun of(slot: Int, slots: Int): BlobKind =
            when {
                slots == 1 -> SURFACE
                slot == slots - 1 -> BIOMES
                else -> SECTION
            }
    }
}
