package io.github.fopwoc.mods.palimpsest.render

import io.github.fopwoc.mods.palimpsest.map.MapPageKey
import io.github.fopwoc.mods.palimpsest.map.MapPageRaster
import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.Sample
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord

/**
 * One 128×128 page from the tree: at LOD 0–3 every pixel is a block sampled from a decoded tile
 * (one tile covers 16 >> lod pixels per side); from LOD 4 up a pixel is a whole square of the
 * quadtree and comes from the parents' sample blocks, never from a tile. [tileAt] lets the live
 * view overlay tiles the broker holds but has not committed yet.
 */
class PageBuilder(
    private val tree: MapTree,
    private val shader: TerrainShader,
    private val tileAt: (TileKey, Long) -> TileRecord? = { key, epoch -> tree.tile(key, epoch) },
) {
    fun build(key: MapPageKey, epoch: Long, checkActive: () -> Unit = {}): MapPageRaster? {
        val grid = SampleGrid(MapPageKey.SIDE)
        val present = if (key.lod < TILE_LOD) fillFromTiles(grid, key, epoch, checkActive) else fillFromSamples(grid, key, epoch)
        checkActive()
        if (!present) return null
        val rgba = ByteArray(MapPageKey.SIDE * MapPageKey.SIDE * 4)
        shader.shade(grid, rgba)
        return MapPageRaster(rgba)
    }

    private fun fillFromSamples(grid: SampleGrid, key: MapPageKey, epoch: Long): Boolean {
        val level = key.lod - TILE_LOD
        val x0 = key.x * MapPageKey.SIDE + (MapTree.OFFSET ushr level) - 1
        val z0 = key.z * MapPageKey.SIDE + (MapTree.OFFSET ushr level) - 1
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

    private fun fillFromTiles(grid: SampleGrid, key: MapPageKey, epoch: Long, checkActive: () -> Unit): Boolean {
        val pixelsPerTile = TileRecord.SIDE shr key.lod
        val step = 1 shl key.lod
        val tilesPerSide = MapPageKey.SIDE / pixelsPerTile
        val firstTileX = key.x * tilesPerSide
        val firstTileZ = key.z * tilesPerSide
        var present = false
        val tiles = HashMap<TileKey, TileRecord?>()
        fun tile(tileX: Int, tileZ: Int): TileRecord? = tiles.getOrPut(TileKey(tileX, tileZ)) { tileAt(TileKey(tileX, tileZ), epoch) }
        for (z in -1 until MapPageKey.SIDE) for (x in -1 until MapPageKey.SIDE) {
            if ((x and (pixelsPerTile - 1)) == 0 && (z and (pixelsPerTile - 1)) == 0 && x >= 0 && z >= 0) checkActive()
            val tileX = firstTileX + Math.floorDiv(x, pixelsPerTile)
            val tileZ = firstTileZ + Math.floorDiv(z, pixelsPerTile)
            val record = tile(tileX, tileZ) ?: continue
            val localX = Math.floorMod(x, pixelsPerTile) * step + step / 2
            val localZ = Math.floorMod(z, pixelsPerTile) * step + step / 2
            val position = localZ * TileRecord.SIDE + localX
            grid.set(x, z, record.block(position), record.height(position), record.depth(position), record.biome(position))
            if (x >= 0 && z >= 0 && record.block(position) > 0) present = true
        }
        return present
    }

    companion object {
        /** The level of detail where one pixel is one tile. */
        const val TILE_LOD = 4
    }
}
