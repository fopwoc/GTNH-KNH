package io.github.fopwoc.palimpsest.db.benchmark

import io.github.fopwoc.palimpsest.db.Biomes
import io.github.fopwoc.palimpsest.db.BlockVocabulary
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.SectionBlocks
import kotlin.random.Random

/** The worst content for the codec: every block independent, every section new every commit. */
class NoiseScenario(vocabulary: BlockVocabulary, kinds: Int, override val commits: Int) : Scenario {
    private val ids = IntArray(kinds) { vocabulary.id("bench:noise:$it").raw }
    private val positions = window(ChunkPos(0, 0), 4).take(64)
    override val seen = positions.toSet()

    override fun observe(index: Int): List<ChunkObservation> = positions.parallelMap { pos ->
        val random = Random(index * 7919L + pos.x * 31 + pos.z)
        val sections =
            List(SyntheticWorld.SECTIONS) {
                SectionBlocks.of(IntArray(SectionBlocks.VOLUME) { ids[random.nextInt(ids.size)] })
            }
        ChunkObservation(
            pos,
            0,
            sections,
            Biomes.Columns(IntArray(Biomes.Columns.COLUMNS) { random.nextInt(40) }),
        )
    }
}
