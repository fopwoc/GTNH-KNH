package io.github.fopwoc.mods.palimpsest.history

import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.Sample
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import io.github.fopwoc.mods.palimpsest.tree.TileSource
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.ChunkWindow
import io.github.fopwoc.palimpsest.db.WorldTick

/**
 * The map's pages read from history: tiles from the database's surfaces, far-zoom squares from its
 * overview. Epochs are world ticks, [Long.MAX_VALUE] the latest commit. Writes are not this
 * source's business: the scanner stages snapshots into history directly. Blocking reads; page
 * builders call them off the game thread.
 */
class HistoryTiles(private val history: DimensionHistory) : TileSource {
    override val latestEpoch: Long
        get() = history.dimension.latest?.tick?.value ?: -1

    override fun tile(key: TileKey, epoch: Long): TileRecord? = tiles(key.x, key.z, 1, epoch)[0]

    override fun tiles(
        x0: Int,
        z0: Int,
        side: Int,
        epoch: Long,
        checkActive: () -> Unit,
    ): Array<TileRecord?> {
        val grid =
            history.dimension
                .at(WorldTick(epoch))
                .surface(ChunkWindow(x0, z0, side, side))
                .result
                .get()
        checkActive()
        val stamp = (history.dimension.latest?.tick?.value ?: 0).coerceAtLeast(0)
        return Array(side * side) { offset ->
            val chunk = ChunkPos(x0 + offset % side, z0 + offset / side)
            if (!grid.present(chunk)) return@Array null
            val bx = chunk.x * TileRecord.SIDE
            val bz = chunk.z * TileRecord.SIDE
            TileRecord.build(
                stamp,
                block = { at ->
                    history.drawnId(
                        grid.block(bx + at % TileRecord.SIDE, bz + at / TileRecord.SIDE).raw
                    )
                },
                height = { at ->
                    grid.height(bx + at % TileRecord.SIDE, bz + at / TileRecord.SIDE)
                },
                depth = { at -> grid.depth(bx + at % TileRecord.SIDE, bz + at / TileRecord.SIDE) },
                biome = { at ->
                    grid.biome(bx + at % TileRecord.SIDE, bz + at / TileRecord.SIDE).raw
                },
            )
        }
    }

    /**
     * Level-[level] squares from the overview; the tree's unsigned square coordinates shift back to
     * chunks.
     */
    override fun samples(level: Int, x0: Int, z0: Int, side: Int, epoch: Long): LongArray {
        val cellX = x0 - (MapTree.OFFSET ushr level)
        val cellZ = z0 - (MapTree.OFFSET ushr level)
        val window = ChunkWindow(cellX shl level, cellZ shl level, side shl level, side shl level)
        val grid = history.dimension.at(WorldTick(epoch)).overview(window, level).result.get()
        return LongArray(side * side) { offset ->
            val x = cellX + offset % side
            val z = cellZ + offset / side
            if (!grid.present(x, z)) Sample.NONE.packed
            else
                Sample(
                        history.drawnId(grid.block(x, z).raw),
                        grid.height(x, z).coerceIn(0, TileRecord.MAX_HEIGHT),
                        grid.depth(x, z),
                        grid.biome(x, z).raw,
                    )
                    .packed
        }
    }

    override fun changedBetween(from: Long, to: Long): Collection<TileKey> =
        history.dimension
            .diff(WorldTick(from), WorldTick(to))
            .result
            .get()
            .changes
            .filter { it.surface || it.appeared }
            .map { TileKey(it.pos.x, it.pos.z) }

    /**
     * Pending tiles always win their square at far zoom; history does not say which chunk it shows.
     */
    override fun representativeTile(level: Int, x: Int, z: Int): TileKey? = null

    override fun write(epoch: Long, changes: Map<TileKey, TileRecord>): Int = 0

    override fun sealIfDue(): Boolean = false

    override fun seal() = Unit

    override fun close() = Unit
}
