package io.github.fopwoc.mods.palimpsest.render.iso

import kotlin.math.floor

/**
 * Where markers land on a map canvas: [camera] places a world point, and a point without a height
 * stands on [groundY] there.
 */
class MarkProjection(val camera: IsoCamera, private val groundY: (x: Int, z: Int) -> Double) {
    fun at(x: Double, y: Double?, z: Double): Pair<Double, Double> {
        val height = y ?: groundY(floor(x).toInt(), floor(z).toInt())
        return camera.screenX(x, z) to camera.screenY(x, height, z)
    }

    /** Whether a mark of [size] pixels around ([x], [y]) shows on the canvas at all. */
    fun onCanvas(x: Double, y: Double, size: Double = 0.0): Boolean =
        x >= -size && y >= -size && x <= camera.width + size && y <= camera.height + size
}
