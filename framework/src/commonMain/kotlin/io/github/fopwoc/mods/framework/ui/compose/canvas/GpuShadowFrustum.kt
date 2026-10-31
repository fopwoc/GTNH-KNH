package io.github.fopwoc.mods.framework.ui.compose.canvas

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Where a scene's shadows are worked out: the box, seen along its light, around the ground a
 * [width] × [height] canvas shows, so a shadow map spends its texels on what is on screen. The
 * ground runs from the lowest to the highest mesh, cut to where meshes lie, and reaches further
 * towards the light, so a hill just off the canvas still casts onto it.
 *
 * Shadows must not crawl as the view pans or meshes stream in, so the box only takes sizes from a
 * fixed ladder and its middle lands on whole texels of a map [texels] a side: while the size holds,
 * every texel covers the same patch of the world from frame to frame.
 */
object GpuShadowFrustum {
    /**
     * A camera taking world points to the light's clip space, -1 to 1 on each axis, depth growing
     * away from the light; null when there is nothing to shade.
     */
    fun of(scene: GpuScene, width: Double, height: Double, texels: Int): GpuSceneCamera? {
        require(texels > 0)
        val meshes = scene.meshes.filter { it.quadCount > 0 }
        if (meshes.isEmpty()) return null
        val world = DoubleArray(BOX) { if (it < AXES) Double.MAX_VALUE else -Double.MAX_VALUE }
        for (mesh in meshes) {
            val origin = doubleArrayOf(mesh.originX, mesh.originY, mesh.originZ)
            for (axis in 0 until AXES) {
                world[axis] = min(world[axis], origin[axis] + mesh.bounds[axis])
                world[axis + AXES] =
                    max(world[axis + AXES], origin[axis] + mesh.bounds[axis + AXES])
            }
        }
        val low = floor(world[1] / HEIGHT_STEP) * HEIGHT_STEP
        val high = ceil(world[AXES + 1] / HEIGHT_STEP) * HEIGHT_STEP
        // The ground under the canvas' corners, at the scene's bottom and top.
        var minX = Double.MAX_VALUE
        var minZ = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxZ = -Double.MAX_VALUE
        for (sx in doubleArrayOf(0.0, width)) for (sy in doubleArrayOf(0.0, height)) for (y in
            doubleArrayOf(low, high)) {
            val (x, z) = scene.camera.groundAt(sx, sy, y) ?: return null
            minX = min(minX, x)
            minZ = min(minZ, z)
            maxX = max(maxX, x)
            maxZ = max(maxZ, z)
        }
        minX = max(minX, world[0])
        minZ = max(minZ, world[2])
        maxX = min(maxX, world[AXES])
        maxZ = min(maxZ, world[AXES + 2])
        if (minX >= maxX || minZ >= maxZ) return null
        val light = scene.light
        // Reach towards the light as far as the tallest rise could throw a shadow, within reason.
        val horizontal = sqrt((light.x * light.x + light.z * light.z).toDouble())
        val reach =
            if (horizontal < EPSILON) 0.0
            else min((high - low) * horizontal / max(light.y.toDouble(), MIN_ELEVATION), MAX_REACH)
        if (horizontal >= EPSILON) {
            val towardX = light.x / horizontal * reach
            val towardZ = light.z / horizontal * reach
            if (towardX > 0) maxX += towardX else minX += towardX
            if (towardZ > 0) maxZ += towardZ else minZ += towardZ
        }
        // The light's axes: right and up across it, forward the way it travels.
        val forward = doubleArrayOf(-light.x.toDouble(), -light.y.toDouble(), -light.z.toDouble())
        val worldUp =
            if (abs(forward[1]) > STEEP) doubleArrayOf(0.0, 0.0, -1.0)
            else doubleArrayOf(0.0, 1.0, 0.0)
        val right = normalize(cross(forward, worldUp))
        val up = cross(right, forward)
        val axes = arrayOf(right, up, forward)
        val box = DoubleArray(BOX) { if (it < AXES) Double.MAX_VALUE else -Double.MAX_VALUE }
        for (x in doubleArrayOf(minX, maxX)) for (y in doubleArrayOf(low, high)) for (z in
            doubleArrayOf(minZ, maxZ)) {
            for ((index, axis) in axes.withIndex()) {
                val along = axis[0] * x + axis[1] * y + axis[2] * z
                box[index] = min(box[index], along)
                box[index + AXES] = max(box[index + AXES], along)
            }
        }
        val rows = DoubleArray(AXES * COLUMNS)
        for ((index, axis) in axes.withIndex()) {
            // Across the light a texel is the grid; along it, a share of the depth will do.
            val cells = if (index < 2) texels else DEPTH_CELLS
            val raw = max((box[index + AXES] - box[index]) / 2, MIN_HALF)
            val half = ladder(raw * (1 + 2.0 / cells))
            val cell = 2 * half / cells
            val middle = round((box[index + AXES] + box[index]) / 2 / cell) * cell
            for (component in 0 until AXES) rows[index * COLUMNS + component] =
                axis[component] / half
            rows[index * COLUMNS + AXES] = -middle / half
        }
        return GpuSceneCamera(rows)
    }

    /** The next size up of the ladder: [RUNGS] to an octave. */
    private fun ladder(size: Double): Double = 2.0.pow(ceil(log2(size) * RUNGS) / RUNGS)

    private fun cross(a: DoubleArray, b: DoubleArray) =
        doubleArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0],
        )

    private fun normalize(v: DoubleArray): DoubleArray {
        val length = sqrt(v.sumOf { it * it })
        return DoubleArray(AXES) { v[it] / length }
    }

    private const val AXES = 3
    private const val BOX = 6
    private const val COLUMNS = 4
    private const val EPSILON = 1e-6
    /** A light this close to straight down measures its sideways axes from north instead. */
    private const val STEEP = 0.99
    private const val MIN_ELEVATION = 0.1
    /** Blocks the box reaches past the canvas towards the light at most. */
    private const val MAX_REACH = 256.0
    private const val MIN_HALF = 1.0
    private const val RUNGS = 8
    /** Steps of the depth range's middle, across its whole length. */
    private const val DEPTH_CELLS = 1024
    /** The ground's height range snaps out to this many blocks, so streaming in rarely moves it. */
    private const val HEIGHT_STEP = 16.0
}
