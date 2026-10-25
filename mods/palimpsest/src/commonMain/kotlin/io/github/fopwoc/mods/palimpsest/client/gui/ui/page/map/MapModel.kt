package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.palimpsest.map.MapTime

data class MapModel(
    val centerX: Double,
    val centerZ: Double,
    /** GUI pixels per block; 1 = one block per pixel, 1/16 = one chunk per pixel. */
    val pixelsPerBlock: Double,
    val time: MapTime,
    /** The snapshot strip while it is open. */
    val history: MapHistoryModel?,
    /** How the map is turned and cut; null when the world has no history to stand columns on. */
    val view: MapViewAngle? = null,
)

data class MapViewAngle(
    val yaw: Double,
    val pitch: Double,
    /** The height in the middle of the 3D view. */
    val centerY: Double,
    /** The highest Y shown, or null for everything. */
    val slice: Int?,
    /** What the slice can be set to, bottom to top. */
    val sliceRange: IntRange,
    /** North up, straight down and uncut: drawn as the flat map. */
    val flat: Boolean,
)

data class MapHistoryModel(
    /** Row labels, live first then commits newest first. */
    val labels: List<String>,
    val entry: Int,
    /** Eased row position of the selection, in entries; where the strip centres itself. */
    val position: Double,
)

/** What the map screen draws over the map: entity dots, and the player's arrow above them. */
class MapOverlay(val dots: GpuCanvasFrame, val marker: GpuCanvasFrame) {
    companion object {
        val EMPTY = MapOverlay(GpuCanvasFrame(emptyList()), GpuCanvasFrame(emptyList()))
    }
}
