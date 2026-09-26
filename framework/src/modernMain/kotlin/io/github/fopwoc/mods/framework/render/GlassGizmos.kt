/*? if >=26 {*/
// Not ported to 1.21.1 yet: the whole file exists only from 26.x.
package io.github.fopwoc.mods.framework.render

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import net.minecraft.gizmos.Gizmo
import net.minecraft.gizmos.GizmoPrimitives
import net.minecraft.gizmos.Gizmos
import net.minecraft.util.ARGB
import net.minecraft.world.phys.Vec3

/**
 * The glass volumes of GTNH's `GlassSurfaces` as Minecraft 26.x gizmos, so they go through the
 * game's graphics backend. Gizmo quads take one color each, so the smooth GL gradients become finer
 * tessellation and banded rims. Must be called while a gizmo collector is active.
 */
object GlassGizmos {
    /** Box drawn over terrain; each face has a bright rim fading into a faint centre. */
    fun box(min: Vec3, max: Vec3, color: Color, eye: Vec3, insideEdges: Boolean = true) {
        val inside = eye.x in min.x..max.x && eye.y in min.y..max.y && eye.z in min.z..max.z
        Gizmos.addGizmo(
                Gizmo { primitives, alphaMultiplier ->
                    val tint = Tint(color, alphaMultiplier)
                    for (face in boxFaces(min, max)) primitives.face(face, eye, tint)
                    // From inside, the faces face the eye and fade out; the twelve edges keep the
                    // box readable.
                    if (insideEdges && inside) {
                        val edge = tint(GRID_STRONG_ALPHA)
                        boxEdges(min, max).forEach { (from, to) ->
                            primitives.addLine(from, to, edge, EDGE_WIDTH)
                        }
                    }
                }
            )
            .setAlwaysOnTop()
    }

    /**
     * Sphere depth-tested against terrain, with the hidden part ghosted over it, so where the shell
     * cuts into blocks shows while the whole shape still reads through walls.
     */
    fun sphere(center: Vec3, radius: Double, color: Color, eye: Vec3, grid: GlassGrid) {
        if (radius <= 0.0) return
        val shell = SphereShell(center, radius)
        Gizmos.addGizmo(sphereGizmo(shell, color, eye, grid, alphaScale = 1f))
        Gizmos.addGizmo(sphereGizmo(shell, color, eye, grid, alphaScale = HIDDEN_ALPHA_SCALE))
            .setAlwaysOnTop()
    }

    private fun sphereGizmo(
        shell: SphereShell,
        color: Color,
        eye: Vec3,
        grid: GlassGrid,
        alphaScale: Float,
    ) = Gizmo { primitives, alphaMultiplier ->
        val tint = Tint(color, alphaMultiplier * alphaScale)
        val (center, radius) = shell
        val inside = eye.distanceToSqr(center) < radius * radius
        val fillAlpha = if (inside) INSIDE_FILL_ALPHA else FILL_ALPHA
        val points = shell.points
        for (stack in 0 until shell.stacks) {
            for (slice in 0 until shell.slices) {
                val a = points[stack][slice]
                val b = points[stack + 1][slice]
                val c = points[stack + 1][slice + 1]
                val d = points[stack][slice + 1]
                val middle =
                    Vec3(
                        (a.x + b.x + c.x + d.x) / 4,
                        (a.y + b.y + c.y + d.y) / 4,
                        (a.z + b.z + c.z + d.z) / 4,
                    )
                val normal = middle.subtract(center).normalize()
                val toEye = eye.subtract(middle)
                val distance = toEye.length().coerceAtLeast(1e-4)
                val rim = (1f - facing(normal, toEye)).let { it * it }
                // Nearer shell brighter than the far side, so an off-centre viewer feels which wall
                // is close.
                val proximity = (1.0 - distance / (2.0 * radius)).coerceIn(0.35, 1.0).toFloat()
                primitives.addQuad(
                    a,
                    b,
                    c,
                    d,
                    tint(fillAlpha * proximity + (RIM_ALPHA - fillAlpha) * rim, light(normal)),
                )
            }
        }
        if (grid == GlassGrid.ALWAYS || grid == GlassGrid.INSIDE && inside)
            primitives.sphereGrid(shell, eye, tint)
    }

    /**
     * A faint latitude/longitude grid (equator and four meridians stronger) so the curvature reads,
     * and a bright ring where the shell crosses eye height.
     */
    private fun GizmoPrimitives.sphereGrid(shell: SphereShell, eye: Vec3, tint: Tint) {
        val points = shell.points
        val step = 2 * SPHERE_SUBDIVISION
        for (stack in step until shell.stacks step step) {
            val line = tint(if (stack == shell.stacks / 2) GRID_STRONG_ALPHA else GRID_ALPHA)
            for (slice in 0 until shell.slices) addLine(
                points[stack][slice],
                points[stack][slice + 1],
                line,
                GRID_WIDTH,
            )
        }
        for (slice in 0 until shell.slices step step) {
            val line = tint(if (slice % (shell.slices / 4) == 0) GRID_STRONG_ALPHA else GRID_ALPHA)
            for (stack in 0 until shell.stacks) addLine(
                points[stack][slice],
                points[stack + 1][slice],
                line,
                GRID_WIDTH,
            )
        }
        val (center, radius) = shell
        val dy = eye.y - center.y
        if (abs(dy) >= radius) return
        val ringRadius = sqrt(radius * radius - dy * dy)
        val ring = tint(RING_ALPHA)
        val segments = shell.slices * 2
        fun ringPoint(index: Int) =
            (2 * PI * index / segments).let {
                Vec3(center.x + cos(it) * ringRadius, eye.y, center.z + sin(it) * ringRadius)
            }
        for (index in 0 until segments) addLine(
            ringPoint(index),
            ringPoint(index + 1),
            ring,
            RING_WIDTH,
        )
    }

    private fun GizmoPrimitives.face(face: BoxFace, eye: Vec3, tint: Tint) {
        val corners = face.corners
        val center = corners[0].lerp(corners[2], 0.5)
        // Faces seen edge-on are brighter, like the sphere rim; faces seen head-on stay faint.
        val faceScale = 0.6f + 0.4f * (1f - facing(face.normal, eye.subtract(center)))
        val light = light(face.normal)
        val rimAlpha = RIM_ALPHA * faceScale
        val inner = List(4) { inset(corners, it) }
        for (band in 0 until RIM_BANDS) {
            val from = band.toDouble() / RIM_BANDS
            val to = (band + 1).toDouble() / RIM_BANDS
            val color =
                tint(rimAlpha + (FILL_ALPHA - rimAlpha) * ((from + to) / 2).toFloat(), light)
            for (index in 0 until 4) {
                val next = (index + 1) % 4
                addQuad(
                    corners[index].lerp(inner[index], from),
                    corners[next].lerp(inner[next], from),
                    corners[next].lerp(inner[next], to),
                    corners[index].lerp(inner[index], to),
                    color,
                )
            }
        }
        addQuad(inner[0], inner[1], inner[2], inner[3], tint(FILL_ALPHA, light))
    }

    /** Moves corner [index] towards the face centre by [BOX_RIM] along each of its two edges. */
    private fun inset(corners: List<Vec3>, index: Int): Vec3 {
        val corner = corners[index]
        val toNext = corners[(index + 1) % 4].subtract(corner)
        val toPrevious = corners[(index + 3) % 4].subtract(corner)
        fun step(delta: Double) = if (abs(delta) > BOX_RIM * 2) BOX_RIM * sign(delta) else delta / 2
        return corner.add(
            step(toNext.x) + step(toPrevious.x),
            step(toNext.y) + step(toPrevious.y),
            step(toNext.z) + step(toPrevious.z),
        )
    }

    private fun sign(value: Double) = if (value < 0) -1.0 else 1.0

    private fun facing(normal: Vec3, toEye: Vec3): Float =
        abs(normal.dot(toEye) / toEye.length().coerceAtLeast(1e-4)).toFloat()

    private fun light(normal: Vec3): Float =
        0.7f +
            0.3f *
                maxOf(0.0, normal.x * LIGHT_X + normal.y * LIGHT_Y + normal.z * LIGHT_Z).toFloat()

    private class BoxFace(val normal: Vec3, vararg corners: Vec3) {
        val corners = corners.toList()
    }

    private fun boxFaces(min: Vec3, max: Vec3): List<BoxFace> {
        val (x0, y0, z0) = min
        val (x1, y1, z1) = max
        return listOf(
            BoxFace(
                Vec3(0.0, 0.0, -1.0),
                Vec3(x0, y1, z0),
                Vec3(x1, y1, z0),
                Vec3(x1, y0, z0),
                Vec3(x0, y0, z0),
            ),
            BoxFace(
                Vec3(0.0, 0.0, 1.0),
                Vec3(x0, y0, z1),
                Vec3(x1, y0, z1),
                Vec3(x1, y1, z1),
                Vec3(x0, y1, z1),
            ),
            BoxFace(
                Vec3(-1.0, 0.0, 0.0),
                Vec3(x0, y0, z0),
                Vec3(x0, y0, z1),
                Vec3(x0, y1, z1),
                Vec3(x0, y1, z0),
            ),
            BoxFace(
                Vec3(1.0, 0.0, 0.0),
                Vec3(x1, y1, z0),
                Vec3(x1, y1, z1),
                Vec3(x1, y0, z1),
                Vec3(x1, y0, z0),
            ),
            BoxFace(
                Vec3(0.0, -1.0, 0.0),
                Vec3(x0, y0, z0),
                Vec3(x1, y0, z0),
                Vec3(x1, y0, z1),
                Vec3(x0, y0, z1),
            ),
            BoxFace(
                Vec3(0.0, 1.0, 0.0),
                Vec3(x0, y1, z1),
                Vec3(x1, y1, z1),
                Vec3(x1, y1, z0),
                Vec3(x0, y1, z0),
            ),
        )
    }

    private fun boxEdges(min: Vec3, max: Vec3): List<Pair<Vec3, Vec3>> = buildList {
        for (y in doubleArrayOf(min.y, max.y)) {
            add(Vec3(min.x, y, min.z) to Vec3(max.x, y, min.z))
            add(Vec3(max.x, y, min.z) to Vec3(max.x, y, max.z))
            add(Vec3(max.x, y, max.z) to Vec3(min.x, y, max.z))
            add(Vec3(min.x, y, max.z) to Vec3(min.x, y, min.z))
        }
        for ((x, z) in listOf(min.x to min.z, max.x to min.z, max.x to max.z, min.x to max.z)) add(
            Vec3(x, min.y, z) to Vec3(x, max.y, z)
        )
    }

    private operator fun Vec3.component1() = x

    private operator fun Vec3.component2() = y

    private operator fun Vec3.component3() = z

    /**
     * Shell vertices shared by the visible and the ghosted pass; twice GTNH's tessellation to hide
     * flat shading.
     */
    private data class SphereShell(val center: Vec3, val radius: Double) {
        val slices = (24 + radius * 2).toInt().coerceIn(24, 64) * SPHERE_SUBDIVISION
        val stacks = slices / 2
        val points: Array<Array<Vec3>> by lazy {
            Array(stacks + 1) { stack ->
                val phi = PI * stack / stacks
                Array(slices + 1) { slice ->
                    val theta = 2 * PI * slice / slices
                    Vec3(
                        center.x + sin(phi) * cos(theta) * radius,
                        center.y + cos(phi) * radius,
                        center.z + sin(phi) * sin(theta) * radius,
                    )
                }
            }
        }
    }

    private class Tint(private val color: Color, private val alphaScale: Float) {
        operator fun invoke(alpha: Float, light: Float = 1f): Int =
            ARGB.colorFromFloat(
                (alpha * alphaScale).coerceIn(0f, 1f),
                color.red / 255f * light,
                color.green / 255f * light,
                color.blue / 255f * light,
            )
    }

    private const val FILL_ALPHA = 0.05f
    // Seen from inside, every point faces the eye and the rim vanishes; keep the shell readable.
    private const val INSIDE_FILL_ALPHA = 0.22f
    private const val GRID_ALPHA = 0.28f
    private const val GRID_STRONG_ALPHA = 0.6f
    private const val RING_ALPHA = 0.9f
    private const val RIM_ALPHA = 0.7f
    private const val HIDDEN_ALPHA_SCALE = 0.3f
    private const val BOX_RIM = 0.12
    private const val RIM_BANDS = 6
    private const val SPHERE_SUBDIVISION = 2
    private const val GRID_WIDTH = 1.5f
    private const val RING_WIDTH = 2.5f
    private const val EDGE_WIDTH = 2f
    private const val LIGHT_X = 0.35
    private const val LIGHT_Y = 0.8
    private const val LIGHT_Z = 0.45
}
/*?}*/
