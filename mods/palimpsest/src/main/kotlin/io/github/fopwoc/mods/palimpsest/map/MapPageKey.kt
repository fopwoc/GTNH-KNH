package io.github.fopwoc.mods.palimpsest.map

/** One 128×128-pixel image; each LOD doubles its world coverage on both axes. */
data class MapPageKey(val x: Int, val z: Int, val lod: Int) {
    init {
        require(lod in 0..MAX_LOD)
    }

    companion object {
        const val SIDE = 128
        const val MAX_LOD = 12
        const val BASE_TILES = SIDE / 16

        fun containingTile(tileX: Int, tileZ: Int, lod: Int): MapPageKey {
            require(lod in 0..MAX_LOD)
            val tiles = BASE_TILES shl lod
            return MapPageKey(Math.floorDiv(tileX, tiles), Math.floorDiv(tileZ, tiles), lod)
        }
    }
}
