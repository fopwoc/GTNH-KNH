package io.github.fopwoc.mods.palimpsest.client.minimap

import io.github.fopwoc.mods.palimpsest.config.MinimapCorner

/** What the minimap shows besides the map itself, which goes straight to its canvas. */
internal data class MinimapModel(
    val screenWidth: Int,
    val screenHeight: Int,
    val corner: MinimapCorner,
    val size: Int,
    /** Block coordinates under the map, or null when they are turned off. */
    val coordinates: String?,
)
