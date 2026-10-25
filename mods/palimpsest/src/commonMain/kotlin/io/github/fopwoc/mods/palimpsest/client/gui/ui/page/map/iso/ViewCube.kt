package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuMesh
import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.render.iso.IsoCamera
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * A direction to look from, as the sum of the cube faces a patch touches: a face looks straight at
 * that side, an edge between two, a corner along the diagonal. Components are -1, 0 or 1.
 */
data class CubeTarget(val x: Int, val y: Int, val z: Int) {
    val isTop: Boolean
        get() = x == 0 && z == 0 && y > 0

    val yaw: Double
        get() = Math.toDegrees(atan2(x.toDouble(), z.toDouble()))

    val pitch: Double
        get() =
            Math.toDegrees(atan2(y.toDouble(), hypot(x.toDouble(), z.toDouble())))
                .coerceIn(IsoCamera.MIN_PITCH, IsoCamera.MAX_PITCH)
}

/** One clickable ninth of a visible face, as a quad on the cube's canvas. */
class CubePatch(val target: CubeTarget, val xs: DoubleArray, val ys: DoubleArray) {
    fun contains(x: Double, y: Double): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val cross = (xs[j] - xs[i]) * (y - ys[i]) - (ys[j] - ys[i]) * (x - xs[i])
            val side = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (side == 0) continue
            if (sign != 0 && side != sign) return false
            sign = side
        }
        return sign != 0
    }
}

/** A face label at its projected centre, when the face turns towards the viewer enough to read. */
class CubeLabel(val text: String, val x: Double, val y: Double)

/**
 * The CAD view cube, seen from the same angle as the map so it always shows how the map lies. Every
 * visible face is split three by three like Autodesk's: the middle looks at the face, the strips at
 * the edges, the corners along the corner. The cube never shows its bottom: the map is only ever
 * seen from above.
 */
class ViewCube(val yaw: Double, val pitch: Double) {
    private val camera =
        IsoCamera(
            0.0,
            0.0,
            0.0,
            SCALE,
            yaw,
            pitch.coerceIn(IsoCamera.MIN_PITCH, IsoCamera.MAX_PITCH),
            SIZE,
            SIZE,
        )

    private val faces = Face.entries.filter { it == Face.TOP || facing(it) > FACING_EPSILON }

    val patches: List<CubePatch> = faces.flatMap(::patchesOf)

    val labels: List<CubeLabel> =
        faces
            .filter { it == Face.TOP || facing(it) > LABEL_FACING }
            .map { face ->
                val (x, y) = project(face, 0.0, 0.0)
                CubeLabel(face.label, x, y)
            }

    fun targetAt(x: Double, y: Double): CubeTarget? =
        patches.firstOrNull { it.contains(x, y) }?.target

    /** Faces in their shade, the hovered patch lit, and the face outlines. */
    fun mesh(hovered: CubeTarget?): GpuMesh {
        val positions = ArrayList<Float>()
        val colors = ArrayList<Int>()
        fun triangle(xs: DoubleArray, ys: DoubleArray, a: Int, b: Int, c: Int, color: Int) {
            for (i in intArrayOf(a, b, c)) {
                positions += xs[i].toFloat()
                positions += ys[i].toFloat()
                colors += color
            }
        }
        for (face in faces) {
            val shade = if (face == Face.TOP) FACE else TerrainShader.shade(FACE, SIDE_SHADE)
            for (patch in patchesOf(face)) {
                val color = if (patch.target == hovered) HOVER else shade
                triangle(patch.xs, patch.ys, 0, 1, 2, color)
                triangle(patch.xs, patch.ys, 0, 2, 3, color)
            }
            val corners = DoubleArray(8)
            for ((i, corner) in CORNERS.withIndex()) {
                val (x, y) = project(face, corner.first, corner.second)
                corners[i * 2] = x
                corners[i * 2 + 1] = y
            }
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                outline(
                    corners[i * 2],
                    corners[i * 2 + 1],
                    corners[j * 2],
                    corners[j * 2 + 1],
                    positions,
                    colors,
                )
            }
        }
        return GpuMesh(positions.toFloatArray(), colors.toIntArray())
    }

    private fun outline(
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
        positions: MutableList<Float>,
        colors: MutableList<Int>,
    ) {
        val length = hypot(bx - ax, by - ay).takeIf { it > 0 } ?: return
        val nx = -(by - ay) / length * EDGE_WIDTH / 2
        val ny = (bx - ax) / length * EDGE_WIDTH / 2
        val xs = doubleArrayOf(ax + nx, bx + nx, bx - nx, ax - nx)
        val ys = doubleArrayOf(ay + ny, by + ny, by - ny, ay - ny)
        for (i in intArrayOf(0, 1, 2, 0, 2, 3)) {
            positions += xs[i].toFloat()
            positions += ys[i].toFloat()
            colors += EDGE
        }
    }

    /** How much of [face] the viewer sees: none edge-on, as straight down sees every side. */
    private fun facing(face: Face): Double =
        (face.x * camera.towardX + face.z * camera.towardZ) * cos(Math.toRadians(camera.pitch))

    private fun patchesOf(face: Face): List<CubePatch> =
        (0 until 3).flatMap { v ->
            (0 until 3).map { u ->
                val du = u - 1
                val dv = v - 1
                val target =
                    CubeTarget(
                        face.x + face.ux * du + face.vx * dv,
                        face.y + face.uy * du + face.vy * dv,
                        face.z + face.uz * du + face.vz * dv,
                    )
                val xs = DoubleArray(4)
                val ys = DoubleArray(4)
                val u0 = BANDS[u]
                val u1 = BANDS[u + 1]
                val v0 = BANDS[v]
                val v1 = BANDS[v + 1]
                for ((i, corner) in listOf(u0 to v0, u1 to v0, u1 to v1, u0 to v1).withIndex()) {
                    val (x, y) = project(face, corner.first, corner.second)
                    xs[i] = x
                    ys[i] = y
                }
                CubePatch(target, xs, ys)
            }
        }

    /** A point of [face] at (u, v) across it, each from -0.5 to 0.5, on the canvas. */
    private fun project(face: Face, u: Double, v: Double): Pair<Double, Double> {
        val x = face.x * HALF + face.ux * u + face.vx * v
        val y = face.y * HALF + face.uy * u + face.vy * v
        val z = face.z * HALF + face.uz * u + face.vz * v
        return camera.screenX(x, z) to camera.screenY(x, y, z)
    }

    /**
     * A face's outward normal and the two axes across it: u to the right and v downwards as the
     * face is seen from outside, the top with north up.
     */
    private enum class Face(
        val x: Int,
        val y: Int,
        val z: Int,
        val ux: Int,
        val uy: Int,
        val uz: Int,
        val vx: Int,
        val vy: Int,
        val vz: Int,
        val label: String,
    ) {
        TOP(0, 1, 0, 1, 0, 0, 0, 0, 1, "TOP"),
        SOUTH(0, 0, 1, 1, 0, 0, 0, -1, 0, "S"),
        EAST(1, 0, 0, 0, 0, -1, 0, -1, 0, "E"),
        NORTH(0, 0, -1, -1, 0, 0, 0, -1, 0, "N"),
        WEST(-1, 0, 0, 0, 0, 1, 0, -1, 0, "W"),
    }

    companion object {
        /** The cube's canvas is this many GUI pixels square. */
        const val SIZE = 64
        private const val SCALE = 30.0
        private const val HALF = 0.5
        /** Where a face is cut into edge strips and middle, from one side to the other. */
        private val BANDS = doubleArrayOf(-0.5, -0.32, 0.32, 0.5)
        private val CORNERS = listOf(-0.5 to -0.5, 0.5 to -0.5, 0.5 to 0.5, -0.5 to 0.5)
        private const val FACING_EPSILON = 1e-6
        private const val LABEL_FACING = 0.35
        private const val FACE = 0xE0D8DCE6.toInt()
        private const val HOVER = 0xF07FB2FF.toInt()
        private const val EDGE = 0xFF20222C.toInt()
        private const val SIDE_SHADE = 190
        private const val EDGE_WIDTH = 1.0
    }
}
