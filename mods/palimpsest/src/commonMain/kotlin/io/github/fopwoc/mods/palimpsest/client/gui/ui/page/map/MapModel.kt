package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import io.github.fopwoc.mods.palimpsest.map.MapTime

data class MapModel(
    val centerX: Double,
    val centerZ: Double,
    /** GUI pixels per block; 1 = one block per pixel, 1/16 = one chunk per pixel. */
    val pixelsPerBlock: Double,
    val time: MapTime,
    /** The snapshot strip while it is open. */
    val history: MapHistoryModel?,
)

data class MapHistoryModel(
    /** Row labels, live first then commits newest first. */
    val labels: List<String>,
    val entry: Int,
    /** Eased row position of the selection, in entries; where the strip centres itself. */
    val position: Double,
)
