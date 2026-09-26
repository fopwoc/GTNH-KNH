package io.github.fopwoc.mods.palimpsest.client.minimap

import io.github.fopwoc.mods.palimpsest.config.MinimapCorner

/** What the minimap shows besides the map itself, which goes straight to its canvas. */
internal data class MinimapModel(
    val screenWidth: Int,
    val screenHeight: Int,
    val layout: MinimapLayout,
    /** Block coordinates under the map, or null when they are turned off. */
    val coordinates: String?,
    /**
     * The centre of the north badge on a turning map, from its top-left in GUI pixels; null when
     * north is up.
     */
    val north: MapMark?,
)

internal sealed interface MinimapLayout {
    /** Width and height of the map canvas in GUI pixels. */
    val mapSize: Pair<Int, Int>

    /** The square minimap in a screen corner. */
    data class Corner(val corner: MinimapCorner, val size: Int) : MinimapLayout {
        override val mapSize
            get() = size to size
    }

    /** The see-through map over most of the screen while its key is held. */
    data class Big(val width: Int, val height: Int) : MinimapLayout {
        override val mapSize
            get() = width to height
    }
}

internal data class MapMark(val x: Int, val y: Int)
