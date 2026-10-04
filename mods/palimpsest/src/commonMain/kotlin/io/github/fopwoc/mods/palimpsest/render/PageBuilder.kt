package io.github.fopwoc.mods.palimpsest.render

import io.github.fopwoc.mods.palimpsest.map.MapPageKey
import io.github.fopwoc.mods.palimpsest.map.MapPageRaster
import io.github.fopwoc.mods.palimpsest.tree.Sample
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import io.github.fopwoc.mods.palimpsest.tree.TileSource

/**
 * One 128×128 page from the tree: at LOD 0–3 every pixel is a block sampled from a decoded tile
 * (one tile covers 16 >> lod pixels per side); from LOD 4 up a pixel is a whole square of the
 * quadtree and comes from the parents' sample blocks, never from a tile. The live view also sees
 * what the broker holds but has not committed yet: [overlay] overlays it below LOD 4 and [pending]
 * fills the squares those tiles fall in above, where the tree has nothing yet.
 */
class PageBuilder(
    private val tree: TileSource,
    private val shader: TerrainShader,
    private val overlay: (TileKey, Long) -> TileRecord? = { _, _ -> null },
    private val pending: () -> Map<TileKey, TileRecord> = { emptyMap() },
) {
    private val shading = PageShading(shader)

    fun build(key: MapPageKey, epoch: Long, checkActive: () -> Unit = {}): MapPageRaster? {
        return rebuild(key, epoch, null, null, checkActive).raster
    }

    internal fun rebuild(
        key: MapPageKey,
        epoch: Long,
        previous: PageSnapshot?,
        changedTiles: Set<TileKey>?,
        checkActive: () -> Unit = {},
    ): PageSnapshot {
        if (previous?.grid != null && changedTiles != null && key.lod < TILE_LOD) {
            val grid = previous.grid.copy()
            for (tile in changedTiles) {
                checkActive()
                refreshTile(grid, key, tile, overlay(tile, epoch) ?: tree.tile(tile, epoch))
            }
            checkActive()
            return if (present(grid)) shading.build(grid, previous) else PageSnapshot.EMPTY
        }
        val grid = SampleGrid(MapPageKey.SIDE)
        var present =
            if (key.lod < TILE_LOD) fillFromTiles(grid, key, epoch, checkActive)
            else fillFromSamples(grid, key, epoch)
        if (key.lod >= TILE_LOD && epoch == Long.MAX_VALUE)
            present = overlayPending(grid, key) || present
        checkActive()
        if (!present) return PageSnapshot.EMPTY
        return shading.build(grid, previous)
    }

    private fun present(grid: SampleGrid): Boolean {
        for (z in 0 until grid.side) for (x in 0 until grid.side) if (grid.isPresent(x, z))
            return true
        return false
    }

    private fun refreshTile(grid: SampleGrid, key: MapPageKey, tile: TileKey, record: TileRecord?) {
        val pixels = TileRecord.SIDE shr key.lod
        val step = 1 shl key.lod
        val across = MapPageKey.SIDE / pixels
        val x0 = (tile.x - key.x * across) * pixels
        val z0 = (tile.z - key.z * across) * pixels
        for (z in maxOf(-1, z0) until minOf(grid.side, z0 + pixels)) for (x in
            maxOf(-1, x0) until minOf(grid.side, x0 + pixels)) {
            val position =
                ((z - z0) * step + step / 2) * TileRecord.SIDE + (x - x0) * step + step / 2
            if (record == null) grid.set(x, z, SampleGrid.NONE, 0, 0, 0)
            else grid.set(x, z, record.sample(position))
        }
    }

    private fun fillFromSamples(grid: SampleGrid, key: MapPageKey, epoch: Long): Boolean {
        val level = key.lod - TILE_LOD
        val x0 = key.x * MapPageKey.SIDE - 1
        val z0 = key.z * MapPageKey.SIDE - 1
        val samples = tree.samples(level, x0, z0, grid.stride, epoch)
        var present = false
        for (z in -1 until MapPageKey.SIDE) for (x in -1 until MapPageKey.SIDE) {
            val sample = Sample(samples[(z + 1) * grid.stride + (x + 1)])
            if (sample.isNone) continue
            grid.set(x, z, sample)
            if (x >= 0 && z >= 0 && sample.block > 0) present = true
        }
        return present
    }

    /** Live tiles not in history yet win their square; the first in Z-order when several do. */
    private fun overlayPending(grid: SampleGrid, key: MapPageKey): Boolean {
        val level = key.lod - TILE_LOD
        val x0 = key.x * MapPageKey.SIDE
        val z0 = key.z * MapPageKey.SIDE
        val selected = HashMap<Pair<Int, Int>, Pair<TileKey, TileRecord>>()
        for ((tile, record) in pending()) {
            val x = Math.floorDiv(tile.x, 1 shl level) - x0
            val z = Math.floorDiv(tile.z, 1 shl level) - z0
            if (x !in -1 until MapPageKey.SIDE || z !in -1 until MapPageKey.SIDE) continue
            val square = x to z
            val previous = selected[square]?.first
            if (previous != null && previous <= tile) continue
            selected[square] = tile to record
        }
        var added = false
        for ((square, candidate) in selected) {
            val (x, z) = square
            val record = candidate.second
            grid.set(x, z, record.sample)
            if (x >= 0 && z >= 0 && record.sample.block > 0) added = true
        }
        return added
    }

    private fun fillFromTiles(
        grid: SampleGrid,
        key: MapPageKey,
        epoch: Long,
        checkActive: () -> Unit,
    ): Boolean {
        val pixelsPerTile = TileRecord.SIDE shr key.lod
        val step = 1 shl key.lod
        val tilesPerSide = MapPageKey.SIDE / pixelsPerTile
        val firstTileX = key.x * tilesPerSide
        val firstTileZ = key.z * tilesPerSide
        var present = false
        val windowSide = tilesPerSide + 1
        val tiles = tree.tiles(firstTileX - 1, firstTileZ - 1, windowSide, epoch, checkActive)
        for (offset in tiles.indices) {
            val tileKey =
                TileKey(firstTileX - 1 + offset % windowSide, firstTileZ - 1 + offset / windowSide)
            overlay(tileKey, epoch)?.let { tiles[offset] = it }
        }
        for (z in -1 until MapPageKey.SIDE) for (x in -1 until MapPageKey.SIDE) {
            if (
                (x and (pixelsPerTile - 1)) == 0 &&
                    (z and (pixelsPerTile - 1)) == 0 &&
                    x >= 0 &&
                    z >= 0
            )
                checkActive()
            val tileX = firstTileX + Math.floorDiv(x, pixelsPerTile)
            val tileZ = firstTileZ + Math.floorDiv(z, pixelsPerTile)
            val record =
                tiles[(tileZ - firstTileZ + 1) * windowSide + tileX - firstTileX + 1] ?: continue
            val localX = Math.floorMod(x, pixelsPerTile) * step + step / 2
            val localZ = Math.floorMod(z, pixelsPerTile) * step + step / 2
            val position = localZ * TileRecord.SIDE + localX
            grid.set(
                x,
                z,
                record.block(position),
                record.height(position),
                record.depth(position),
                record.biome(position),
            )
            if (x >= 0 && z >= 0 && record.block(position) > 0) present = true
        }
        return present
    }

    companion object {
        /** The level of detail where one pixel is one tile. */
        const val TILE_LOD = 4
    }
}
