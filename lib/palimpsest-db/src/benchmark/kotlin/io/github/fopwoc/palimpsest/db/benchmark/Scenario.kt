package io.github.fopwoc.palimpsest.db.benchmark

import io.github.fopwoc.palimpsest.db.BlockVocabulary
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos

/** A workload: what the game would hand the database at each commit. */
interface Scenario {
    val commits: Int

    /** Every chunk observed so far, for read probes. */
    val seen: Set<ChunkPos>

    /** Observations of commit [index]; producing them is not part of the measurement. */
    fun observe(index: Int): List<ChunkObservation>

    class Kind(
        val name: String,
        val realistic: Boolean,
        val summary: String,
        val create: (BlockVocabulary) -> Scenario,
    )

    companion object {
        val ALL =
            listOf(
                Kind("explore", true, "walks 3 chunks per commit, radius 8 window") {
                    ExploreScenario(it)
                },
                Kind("build", true, "stays home, 30 blocks in 3 chunks per commit") {
                    BaseScenario(it, BaseScenario.Activity.BUILD)
                },
                Kind("decay", true, "stays home, one leaf flips in 10% of chunks per commit") {
                    BaseScenario(it, BaseScenario.Activity.DECAY)
                },
                Kind(
                    "noise",
                    false,
                    "64 chunks of uniform noise over 64 blocks, all new each commit",
                ) {
                    NoiseScenario(it, kinds = 64, commits = 30)
                },
                Kind("palette", false, "64 chunks of noise over 1500 blocks, all new each commit") {
                    NoiseScenario(it, kinds = 1500, commits = 15)
                },
                Kind("churn", false, "radius 6 window, the whole terrain regenerates each commit") {
                    ChurnScenario(it)
                },
            )
    }
}

fun window(center: ChunkPos, radius: Int): List<ChunkPos> =
    (-radius..radius).flatMap { dz ->
        (-radius..radius).map { dx -> ChunkPos(center.x + dx, center.z + dz) }
    }

fun <T, R> List<T>.parallelMap(transform: (T) -> R): List<R> =
    parallelStream().map(transform).toList()
