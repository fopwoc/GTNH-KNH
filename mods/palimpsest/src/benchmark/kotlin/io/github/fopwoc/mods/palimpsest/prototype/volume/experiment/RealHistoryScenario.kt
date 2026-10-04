package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.ChunkVolume
import io.github.fopwoc.mods.palimpsest.prototype.volume.save.LegacySave
import io.github.fopwoc.mods.palimpsest.tree.TileKey

/**
 * Two saves of one world months apart: everything in [before] is committed first, then [after].
 * Chunks present in both are revisits and pay only for what really changed; chunks only in
 * [after] are new exploration. The two kinds are committed and measured separately.
 */
class RealHistoryScenario(
    private val before: LegacySave,
    private val after: LegacySave,
    private val stores: PairedStores,
    private val perCommit: Int = 64,
) {
    class Phase(
        val chunks: Int,
        val versions: Long,
        val sections: Long,
        val surfaceBytes: Long,
        val volumeBytes: Long,
        val treeBytes: Long,
    )

    class Result(val initial: Phase, val revisited: Phase, val explored: Phase)

    fun run(): Result {
        var epoch = 1L
        fun phase(volumes: List<Pair<TileKey, ChunkVolume>>): Phase {
            val versions = stores.chunkVersions
            val sections = stores.sectionsWritten
            val surface = stores.surfaceBytes()
            val volume = stores.volumes.bytes
            val tree = stores.tree3d.bytes
            for (group in volumes.sortedBy { morton(it.first.x, it.first.z) }.chunked(perCommit))
                stores.commit(epoch++, group.toMap())
            return Phase(
                volumes.size,
                stores.chunkVersions - versions,
                stores.sectionsWritten - sections,
                stores.surfaceBytes() - surface,
                stores.volumes.bytes - volume,
                stores.tree3d.bytes - tree,
            )
        }
        val initial = phase(before.regions().flatMap { before.volumes(it) })
        val known = stores.volumes.keys().toSet()
        val (revisits, fresh) = after.regions().flatMap { after.volumes(it) }.partition { it.first in known }
        return Result(initial, phase(revisits), phase(fresh))
    }
}
