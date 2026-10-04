package io.github.fopwoc.palimpsest.db

/**
 * A chunk as the game saw it at commit time. [sections] run upward from [minSection] (0 on 1.7.10,
 * −4 on 1.21+); a null section holds only air. Immutable once handed over: the game copies, the
 * database's threads do everything else.
 */
class ChunkObservation(
    val pos: ChunkPos,
    val minSection: Int,
    sections: List<SectionBlocks?>,
    val biomes: Biomes,
) {
    val sections: List<SectionBlocks?> = sections.toList()

    init {
        require(this.sections.size in 1..MAX_SECTIONS) {
            "Unsupported height: ${sections.size} sections"
        }
    }

    companion object {
        /** Slot masks are longs: 62 sections and the biomes keep the top bit clear. */
        const val MAX_SECTIONS = 62
    }
}
