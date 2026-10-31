package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord

/** The newest look of tiles, as the live map draws them, and word of the ones that changed. */
interface LiveTiles {
    /** How these tiles' facts are coloured. */
    val shader: TerrainShader

    /** [width] × [height] tiles from ([x0], [z0]), row by row; null where none was seen. */
    fun tiles(x0: Int, z0: Int, width: Int, height: Int): Array<TileRecord?>

    fun addChangeListener(listener: (Collection<TileKey>) -> Unit)

    fun removeChangeListener(listener: (Collection<TileKey>) -> Unit)
}
