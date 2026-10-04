package io.github.fopwoc.palimpsest.db.benchmark

import io.github.fopwoc.palimpsest.db.BlockVocabulary
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos

/** Realistic content, unrealistic history: the whole loaded terrain is different every commit. */
class ChurnScenario(private val vocabulary: BlockVocabulary, override val commits: Int = 30) :
    Scenario {
    private val positions = window(ChunkPos(0, 0), 6)
    override val seen = positions.toSet()

    override fun observe(index: Int): List<ChunkObservation> {
        val world = SyntheticWorld(1000L + index, vocabulary)
        return positions.parallelMap { world.observe(it, world.sections(it)) }
    }
}
