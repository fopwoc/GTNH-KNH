package io.github.fopwoc.palimpsest.db.store

internal enum class BlobKind {
    SECTION,
    BIOMES;

    companion object {
        /** Slots run sections bottom-up, then the biomes: the kind follows from the slot alone. */
        fun of(slot: Int, slots: Int): BlobKind = if (slot == slots - 1) BIOMES else SECTION
    }
}
