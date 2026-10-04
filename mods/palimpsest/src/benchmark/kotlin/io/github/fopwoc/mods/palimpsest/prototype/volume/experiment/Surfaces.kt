package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

import io.github.fopwoc.mods.framework.world.TileScanner
import io.github.fopwoc.mods.palimpsest.prototype.volume.model.BlockKind
import io.github.fopwoc.mods.palimpsest.prototype.volume.model.ChunkVolume
import io.github.fopwoc.mods.palimpsest.prototype.volume.model.VolumeColumns
import io.github.fopwoc.mods.palimpsest.tree.TileRecord

/** The top-down view of [volume] from [ceiling], through the production scanner. */
fun surface(
    volume: ChunkVolume,
    kinds: Array<BlockKind>,
    epoch: Long,
    ceiling: Int = 255,
): TileRecord {
    val scan = TileScanner.scan(VolumeColumns(volume, kinds), ceiling)
    return TileRecord.build(
        epoch,
        scan.block::get,
        scan.height::get,
        scan.depth::get,
        scan.biome::get,
    )
}
