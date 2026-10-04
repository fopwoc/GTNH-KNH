package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

import io.github.fopwoc.mods.palimpsest.prototype.volume.save.LegacySave
import io.github.fopwoc.mods.palimpsest.prototype.volume.storage.SectionCodec

/**
 * First sight of a whole world: every populated chunk of a save, region by region in Z-order,
 * [perCommit] chunks per epoch, as a player's scanner would report a long journey.
 */
class ExplorationScenario(
    private val save: LegacySave,
    private val stores: PairedStores,
    private val perCommit: Int = 64,
    private val codecSampleEvery: Int = 16,
) {
    class Result(
        val chunks: Int,
        val commits: Int,
        val surfaceBytes: Long,
        val volumeBytes: Long,
        val rangeCodedSample: Long,
        val deflatedSample: Long,
        val sampledSections: Int,
    )

    fun run(firstEpoch: Long = 1): Result {
        var epoch = firstEpoch
        var chunks = 0
        var rangeCoded = 0L
        var deflated = 0L
        var sampled = 0
        for (region in save.regions()) {
            val volumes =
                save.volumes(region).sortedBy { (key, _) -> morton(key.x, key.z) }.toList()
            for ((index, entry) in volumes.withIndex()) {
                if (index % codecSampleEvery != 0) continue
                for (section in 0 until 16) {
                    val blocks = entry.second.section(section) ?: continue
                    rangeCoded += SectionCodec.encode(blocks).size
                    deflated += SectionCodec.deflatedSize(blocks)
                    sampled++
                }
            }
            for (group in volumes.chunked(perCommit)) {
                stores.commit(epoch++, group.toMap())
                chunks += group.size
            }
        }
        return Result(
            chunks,
            (epoch - firstEpoch).toInt(),
            stores.surfaceBytes(),
            stores.volumes.bytes,
            rangeCoded,
            deflated,
            sampled,
        )
    }
}
