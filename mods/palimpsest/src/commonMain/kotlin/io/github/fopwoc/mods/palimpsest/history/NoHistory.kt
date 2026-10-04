package io.github.fopwoc.mods.palimpsest.history

import io.github.fopwoc.mods.palimpsest.tree.Sample
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import io.github.fopwoc.mods.palimpsest.tree.TileSource

/** The map without history, when it could not be opened: only what the live scan shows. */
object NoHistory : TileSource {
    override val latestEpoch: Long = -1

    override fun tile(key: TileKey, epoch: Long): TileRecord? = null

    override fun samples(level: Int, x0: Int, z0: Int, side: Int, epoch: Long): LongArray =
        LongArray(side * side) { Sample.NONE.packed }

    override fun representativeTile(level: Int, x: Int, z: Int): TileKey? = null

    override fun write(epoch: Long, changes: Map<TileKey, TileRecord>): Int = 0

    override fun sealIfDue(): Boolean = false

    override fun seal() = Unit

    override fun close() = Unit
}
