package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.render.GlassGizmos
import io.github.fopwoc.mods.framework.render.GlassGrid
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import net.minecraft.gizmos.GizmoProperties
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Minecraft 26.2 Gizmos draw through the game's graphics backend, including Vulkan. Like the GTNH
 * overlay, shapes draw over terrain unless inside the visible pass of [ghosted].
 */
internal class ModernMeasurementWorldCanvas(private val eye: Vec3) : MeasurementWorldCanvas {
    override val eyeX: Double get() = eye.x
    override val eyeY: Double get() = eye.y
    override val eyeZ: Double get() = eye.z

    private var depthTested = false

    private fun GizmoProperties.layer() {
        if (!depthTested) setAlwaysOnTop()
    }

    override fun line(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, color: Color, width: Float) {
        Gizmos.line(Vec3(x1, y1, z1), Vec3(x2, y2, z2), color.argbInt, width).layer()
    }

    override fun blockOutline(x: Int, y: Int, z: Int, color: Color, width: Float) {
        Gizmos.cuboid(AABB(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0), GizmoStyle.stroke(color.argbInt, width)).layer()
    }

    override fun cornerBrackets(
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double,
        color: Color, width: Float, arm: Double, grow: Double,
    ) {
        val x0 = minX - grow
        val y0 = minY - grow
        val z0 = minZ - grow
        val x1 = maxX + grow
        val y1 = maxY + grow
        val z1 = maxZ + grow
        for (x in doubleArrayOf(x0, x1)) {
            val dx = if (x == x0) arm else -arm
            for (y in doubleArrayOf(y0, y1)) {
                val dy = if (y == y0) arm else -arm
                for (z in doubleArrayOf(z0, z1)) {
                    val dz = if (z == z0) arm else -arm
                    line(x, y, z, x + dx, y, z, color, width)
                    line(x, y, z, x, y + dy, z, color, width)
                    line(x, y, z, x, y, z + dz, color, width)
                }
            }
        }
    }

    override fun filledBox(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, color: Color) {
        Gizmos.cuboid(AABB(minX, minY, minZ, maxX, maxY, maxZ), GizmoStyle.fill(color.argbInt)).layer()
    }

    override fun glassBox(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, color: Color) =
        GlassGizmos.box(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ), color, eye)

    override fun glassSphere(centerX: Double, centerY: Double, centerZ: Double, radius: Double, color: Color, grid: GlassGrid) =
        GlassGizmos.sphere(Vec3(centerX, centerY, centerZ), radius, color, eye, grid)

    override fun label(x: Double, y: Double, z: Double, text: String, color: Color) {
        Gizmos.billboardText(text, Vec3(x, y, z), TextGizmo.Style.forColorAndCentered(color.argbInt).withScale(LABEL_SCALE))
            .setAlwaysOnTop()
    }

    /** The visible pass is depth-tested; the ghost pass draws over everything, like GTNH's `GL_GREATER` pass. */
    override fun ghosted(draw: (Int?) -> Unit) {
        depthTested = true
        try {
            draw(null)
        } finally {
            depthTested = false
        }
        draw(70)
    }

    private companion object {
        // The gizmo renderer divides text scale by 16; this matches GTNH's 0.026 world units per font pixel.
        const val LABEL_SCALE = 0.026f * 16
    }
}
