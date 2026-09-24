package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.render.GlassGrid
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import net.minecraft.gizmos.Gizmo
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** Minecraft 26.2 Gizmos draw through the game's graphics backend, including Vulkan. */
internal class ModernMeasurementWorldCanvas(private val eye: Vec3) : MeasurementWorldCanvas {
    override val eyeX: Double get() = eye.x
    override val eyeY: Double get() = eye.y
    override val eyeZ: Double get() = eye.z

    private var ghost = false

    override fun line(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, color: Color, width: Float) {
        Gizmos.line(Vec3(x1, y1, z1), Vec3(x2, y2, z2), color.argbInt, width).apply {
            if (ghost) setAlwaysOnTop()
        }
    }

    override fun blockOutline(x: Int, y: Int, z: Int, color: Color, width: Float) {
        outline(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0, color, width)
    }

    private fun outline(x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double, color: Color, width: Float) {
        Gizmos.cuboid(AABB(x0, y0, z0, x1, y1, z1), GizmoStyle.stroke(color.argbInt, width)).apply {
            if (ghost) setAlwaysOnTop()
        }
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
        Gizmos.cuboid(AABB(minX, minY, minZ, maxX, maxY, maxZ), GizmoStyle.fill(color.argbInt)).apply {
            if (ghost) setAlwaysOnTop()
        }
    }

    override fun glassBox(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, color: Color) {
        val fill = color.copy(alpha = (color.alpha / 3).coerceAtLeast(12))
        val bounds = AABB(minX, minY, minZ, maxX, maxY, maxZ)
        Gizmos.cuboid(bounds, GizmoStyle.strokeAndFill(color.argbInt, 1.5f, fill.argbInt))
        val hidden = color.copy(alpha = color.alpha * 70 / 255)
        val hiddenFill = fill.copy(alpha = fill.alpha * 70 / 255)
        Gizmos.cuboid(bounds, GizmoStyle.strokeAndFill(hidden.argbInt, 1.5f, hiddenFill.argbInt))
            .setAlwaysOnTop()
    }

    override fun glassSphere(centerX: Double, centerY: Double, centerZ: Double, radius: Double, color: Color, grid: GlassGrid) {
        if (radius <= 0.0) return
        val center = Vec3(centerX, centerY, centerZ)
        val inside = eye.distanceToSqr(center) < radius * radius
        val fill = color.copy(alpha = if (inside) 48 else 16)
        val slices = (24 + radius * 2).toInt().coerceIn(24, 64)
        val stacks = slices / 2
        Gizmos.addGizmo(Gizmo { primitives, _ ->
            for (stack in 0 until stacks) {
                val phi0 = PI * stack / stacks
                val phi1 = PI * (stack + 1) / stacks
                for (slice in 0 until slices) {
                    val theta0 = 2 * PI * slice / slices
                    val theta1 = 2 * PI * (slice + 1) / slices
                    primitives.addQuad(
                        spherePoint(center, radius, phi0, theta0),
                        spherePoint(center, radius, phi1, theta0),
                        spherePoint(center, radius, phi1, theta1),
                        spherePoint(center, radius, phi0, theta1),
                        fill.argbInt,
                    )
                }
            }
            if (grid == GlassGrid.ALWAYS || inside) {
                for (stack in 2 until stacks step 2) {
                    val phi = PI * stack / stacks
                    for (slice in 0 until slices) {
                        primitives.addLine(
                            spherePoint(center, radius, phi, 2 * PI * slice / slices),
                            spherePoint(center, radius, phi, 2 * PI * (slice + 1) / slices),
                            color.argbInt,
                            1.0f,
                        )
                    }
                }
                for (slice in 0 until slices step 2) {
                    val theta = 2 * PI * slice / slices
                    for (stack in 0 until stacks) {
                        primitives.addLine(
                            spherePoint(center, radius, PI * stack / stacks, theta),
                            spherePoint(center, radius, PI * (stack + 1) / stacks, theta),
                            color.argbInt,
                            1.0f,
                        )
                    }
                }
                val eyeOffset = eye.y - centerY
                if (inside && kotlin.math.abs(eyeOffset) < radius) {
                    val ringRadius = sqrt(radius * radius - eyeOffset * eyeOffset)
                    for (segment in 0 until slices * 2) {
                        val angle0 = PI * segment / slices
                        val angle1 = PI * (segment + 1) / slices
                        primitives.addLine(
                            Vec3(centerX + cos(angle0) * ringRadius, eye.y, centerZ + sin(angle0) * ringRadius),
                            Vec3(centerX + cos(angle1) * ringRadius, eye.y, centerZ + sin(angle1) * ringRadius),
                            color.argbInt,
                            2.5f,
                        )
                    }
                }
            }
        }).setAlwaysOnTop()
    }

    override fun label(x: Double, y: Double, z: Double, text: String, color: Color) {
        Gizmos.billboardText(text, Vec3(x, y, z), TextGizmo.Style.forColorAndCentered(color.argbInt).withScale(0.026f))
            .setAlwaysOnTop()
    }

    override fun ghosted(draw: (Int?) -> Unit) {
        draw(null)
        ghost = true
        try {
            draw(70)
        } finally {
            ghost = false
        }
    }

    private fun spherePoint(center: Vec3, radius: Double, phi: Double, theta: Double) =
        Vec3(
            center.x + sin(phi) * cos(theta) * radius,
            center.y + cos(phi) * radius,
            center.z + sin(phi) * sin(theta) * radius,
        )
}
