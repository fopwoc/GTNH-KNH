package io.github.fopwoc.mods.framework.ui.compose.canvas

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

    private companion object {
        const val ROWS = 3
        const val COLUMNS = 4
    }
}
