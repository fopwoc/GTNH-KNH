package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import io.github.fopwoc.mods.palimpsest.map.MapTime
import kotlin.math.pow

/** Where the map screen looks and when; owned by the screen so input handlers and UI share it. */
class MapViewState(centerX: Double, centerZ: Double) {
    var centerX by mutableDoubleStateOf(centerX)
    var centerZ by mutableDoubleStateOf(centerZ)

    /** GUI pixels per block; 1 = one block per pixel, 1/16 = one chunk per pixel. */
    var pixelsPerBlock by mutableDoubleStateOf(1.0)
    var time by mutableStateOf<MapTime>(MapTime.Live)

    fun camera(width: Int, height: Int): MapCamera =
        MapCamera(centerX, centerZ, pixelsPerBlock, width.coerceAtLeast(1), height.coerceAtLeast(1))

    fun panPixels(dx: Double, dy: Double) {
        centerX -= dx / pixelsPerBlock
        centerZ -= dy / pixelsPerBlock
    }

    /** Zooms by [steps] wheel notches around a screen point so the block under the cursor stays. */
    fun zoom(steps: Int, atX: Double, atZ: Double, width: Int, height: Int) {
        val before = pixelsPerBlock
        val after =
            (before * ZOOM_STEP.pow(steps)).coerceIn(MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK)
        if (after == before) return
        val worldX = centerX + (atX - width / 2.0) / before
        val worldZ = centerZ + (atZ - height / 2.0) / before
        pixelsPerBlock = after
        centerX = worldX - (atX - width / 2.0) / after
        centerZ = worldZ - (atZ - height / 2.0) / after
    }

    companion object {
        const val ZOOM_STEP = 1.25
        const val MIN_PIXELS_PER_BLOCK = 1.0 / 4096
        const val MAX_PIXELS_PER_BLOCK = 8.0
    }
}
