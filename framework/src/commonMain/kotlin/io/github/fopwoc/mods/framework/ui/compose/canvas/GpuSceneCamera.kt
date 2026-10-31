package io.github.fopwoc.mods.framework.ui.compose.canvas

import kotlin.math.abs

/**
 * An affine view of world space: [rows] are three rows of four, taking a world point (x, y, z, 1)
 * to the canvas's local GUI x, local GUI y, and a depth from -1 (nearest) to 1 (farthest). Anything
 * outside that depth is clipped, so the camera's owner sizes it to what it shows.
 */
class GpuSceneCamera(rows: DoubleArray) {
    private val rows = rows.copyOf()

    init {
        require(rows.size == ROWS * COLUMNS) { "Three rows of four" }
        require(rows.all(Double::isFinite))
    }

    /**
     * The same transform for points given relative to ([x], [y], [z]), as floats row by row:
     * subtracting large origins in doubles first keeps distant meshes from jittering.
     */
    fun relativeTo(x: Double, y: Double, z: Double): FloatArray =
        FloatArray(ROWS * COLUMNS) { at ->
            val row = at / COLUMNS
            when (val column = at % COLUMNS) {
                COLUMNS - 1 ->
                    (rows[row * COLUMNS] * x +
                            rows[row * COLUMNS + 1] * y +
                            rows[row * COLUMNS + 2] * z +
                            rows[row * COLUMNS + 3])
                        .toFloat()
                else -> rows[row * COLUMNS + column].toFloat()
            }
        }

    /**
     * The world point at height [y] that lands on canvas point ([screenX], [screenY]), as x to z;
     * null when the view runs along the ground there.
     */
    fun groundAt(screenX: Double, screenY: Double, y: Double): Pair<Double, Double>? {
        val ax = rows[0]
        val az = rows[2]
        val bx = rows[COLUMNS]
        val bz = rows[COLUMNS + 2]
        val determinant = ax * bz - az * bx
        if (abs(determinant) < EPSILON) return null
        val u = screenX - rows[1] * y - rows[3]
        val v = screenY - rows[COLUMNS + 1] * y - rows[COLUMNS + 3]
        return (u * bz - az * v) / determinant to (ax * v - u * bx) / determinant
    }

    private companion object {
        const val EPSILON = 1e-12
        const val ROWS = 3
        const val COLUMNS = 4
    }
}
