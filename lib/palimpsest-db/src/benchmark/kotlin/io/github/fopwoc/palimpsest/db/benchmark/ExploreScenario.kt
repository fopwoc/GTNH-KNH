package io.github.fopwoc.palimpsest.db.benchmark

import io.github.fopwoc.palimpsest.db.BlockVocabulary
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import kotlin.math.sin

/** A player travelling: the loaded window slides along a wavy path, mostly into unseen terrain. */
class ExploreScenario(vocabulary: BlockVocabulary, override val commits: Int = 150) : Scenario {
    private val world = SyntheticWorld(SEED, vocabulary)
    private val loaded = HashMap<ChunkPos, ChunkObservation>()
    override val seen = HashSet<ChunkPos>()

    override fun observe(index: Int): List<ChunkObservation> {
        val center = ChunkPos(index * STEP, (sin(index / 15.0) * 20).toInt())
        val positions = window(center, RADIUS)
        loaded.keys.retainAll(positions.toSet())
        positions
            .filter { it !in loaded }
            .parallelMap { it to world.observe(it, world.sections(it)) }
            .forEach { (pos, chunk) -> loaded[pos] = chunk }
        seen += positions
        return positions.map(loaded::getValue)
    }

    private companion object {
        const val SEED = 42L
        const val STEP = 3
        const val RADIUS = 8
    }
}
