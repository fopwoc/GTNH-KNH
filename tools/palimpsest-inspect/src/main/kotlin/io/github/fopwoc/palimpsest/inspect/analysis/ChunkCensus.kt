package io.github.fopwoc.palimpsest.inspect.analysis

import io.github.fopwoc.palimpsest.db.BlockId
import io.github.fopwoc.palimpsest.db.BlockVocabulary
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.Dimension

/** Every distinct version of one chunk through history, oldest first. */
class ChunkCensus(private val dimension: Dimension, private val vocabulary: BlockVocabulary) {
    /**
     * One version as of commit [tick]: its fingerprint, blocks per section from the lowest, and its
     * most common identities with their counts.
     */
    class Version(
        val tick: Long,
        val observedAt: Long,
        val fingerprint: Int,
        val sections: IntArray,
        val common: List<Pair<String, Int>>,
    )

    fun versions(pos: ChunkPos, commonCount: Int = COMMON): List<Version> {
        dimension.ready.get()
        val out = ArrayList<Version>()
        for (commit in dimension.timeline()) {
            val volume = dimension.at(commit.tick).volume(pos).result.get() ?: continue
            val fingerprint = VolumeComparison.fingerprint(volume)
            if (out.lastOrNull()?.fingerprint == fingerprint) continue
            val sections = IntArray((volume.maxY - volume.minY + SECTION - 1) / SECTION)
            val counts = HashMap<BlockId, Int>()
            for (y in volume.minY until volume.maxY) for (z in 0 until SECTION) for (x in
                0 until SECTION) {
                val block = volume.block(x, y, z)
                if (block == BlockId.AIR) continue
                sections[(y - volume.minY) / SECTION]++
                counts.merge(block, 1, Int::plus)
            }
            out +=
                Version(
                    commit.tick.value,
                    commit.observedAt,
                    fingerprint,
                    sections,
                    counts.entries
                        .sortedByDescending { it.value }
                        .take(commonCount)
                        .map { vocabulary.identity(it.key) to it.value },
                )
        }
        return out
    }

    private companion object {
        const val SECTION = 16
        const val COMMON = 3
    }
}
