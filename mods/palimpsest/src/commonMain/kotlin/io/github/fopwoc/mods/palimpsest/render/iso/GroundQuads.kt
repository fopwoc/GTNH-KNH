package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuMesh
import kotlin.math.floor

/**
 * Squares of colour lying on the ground, seen through [camera] as canvas triangles: chunk claims,
 * the tiles a snapshot changed. Each lies at [groundY] under its middle; squares wholly off the
 * canvas are left out.
 */
class GroundQuads(private val camera: IsoCamera, private val groundY: (x: Int, z: Int) -> Double) {
    private var positions = FloatArray(INITIAL_VERTICES * 2)
    private var colors = IntArray(INITIAL_VERTICES)
    private var count = 0

    /**
     * The square from ([minX], [minZ]) to ([maxX], [maxZ]) in blocks, in ARGB [color], at height
     * [y] or on the ground under its middle.
     */
    fun add(minX: Double, minZ: Double, maxX: Double, maxZ: Double, color: Int, y: Double? = null) {
        val y = y ?: groundAt((minX + maxX) / 2, (minZ + maxZ) / 2)
        val xs = doubleArrayOf(minX, maxX, maxX, minX)
        val zs = doubleArrayOf(minZ, minZ, maxZ, maxZ)
        val sx = DoubleArray(CORNERS) { camera.screenX(xs[it], zs[it]) }
        val sy = DoubleArray(CORNERS) { camera.screenY(xs[it], y, zs[it]) }
        if (sx.max() < 0 || sy.max() < 0 || sx.min() > camera.width || sy.min() > camera.height)
            return
        if (count + TRIANGLE_VERTICES > colors.size) {
            positions = positions.copyOf(positions.size * 2)
            colors = colors.copyOf(colors.size * 2)
        }
        for (corner in QUAD_TRIANGLES) {
            positions[count * 2] = sx[corner].toFloat()
            positions[count * 2 + 1] = sy[corner].toFloat()
            colors[count] = color
            count++
        }
    }

    fun groundAt(x: Double, z: Double): Double = groundY(floor(x).toInt(), floor(z).toInt())

    fun build(): GpuMesh =
        if (count == 0) GpuMesh.EMPTY
        else GpuMesh(positions.copyOf(count * 2), colors.copyOf(count))

    private companion object {
        const val CORNERS = 4
        const val TRIANGLE_VERTICES = 6
        val QUAD_TRIANGLES = intArrayOf(0, 1, 2, 0, 2, 3)
        const val INITIAL_VERTICES = 1 shl 10
    }
}
