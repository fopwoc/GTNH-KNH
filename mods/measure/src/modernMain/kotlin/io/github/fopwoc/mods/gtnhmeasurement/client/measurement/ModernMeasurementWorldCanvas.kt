package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.render.GlassGizmos
import io.github.fopwoc.mods.framework.render.GlassGrid
import io.github.fopwoc.mods.framework.render.WorldShapes
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Draws through the framework's [WorldShapes] (26.x gizmos, or 1.21.1's own renderer). Like the
 * GTNH overlay, shapes draw over terrain unless inside the visible pass of [ghosted].
 */
internal class ModernMeasurementWorldCanvas(private val eye: Vec3) : MeasurementWorldCanvas {
    override val eyeX: Double
        get() = eye.x

    override val eyeY: Double
        get() = eye.y

    override val eyeZ: Double
        get() = eye.z

    private var depthTested = false

    override fun line(
        x1: Double,
        y1: Double,
        z1: Double,
        x2: Double,
        y2: Double,
        z2: Double,
        color: Color,
        width: Float,
    ) {
        WorldShapes.line(Vec3(x1, y1, z1), Vec3(x2, y2, z2), color.argbInt, width, !depthTested)
    }

    override fun blockOutline(x: Int, y: Int, z: Int, color: Color, width: Float) {
        WorldShapes.boxOutline(
            AABB(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0),
            color.argbInt,
            width,
            !depthTested,
        )
    }

    override fun cornerBrackets(
        minX: Double,
        minY: Double,
        minZ: Double,
        maxX: Double,
        maxY: Double,
        maxZ: Double,
        color: Color,
        width: Float,
        arm: Double,
        grow: Double,
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

    override fun filledBox(
        minX: Double,
        minY: Double,
        minZ: Double,
        maxX: Double,
        maxY: Double,
        maxZ: Double,
        color: Color,
    ) {
        WorldShapes.box(AABB(minX, minY, minZ, maxX, maxY, maxZ), color.argbInt, !depthTested)
    }

    override fun glassBox(
        minX: Double,
        minY: Double,
        minZ: Double,
        maxX: Double,
        maxY: Double,
        maxZ: Double,
        color: Color,
    ) = GlassGizmos.box(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ), color, eye)

    override fun glassSphere(
        centerX: Double,
        centerY: Double,
        centerZ: Double,
        radius: Double,
        color: Color,
        grid: GlassGrid,
    ) = GlassGizmos.sphere(Vec3(centerX, centerY, centerZ), radius, color, eye, grid)

    override fun label(x: Double, y: Double, z: Double, text: String, color: Color) {
        WorldShapes.text(text, Vec3(x, y, z), color.argbInt, LABEL_SCALE)
    }

    /**
     * The visible pass is depth-tested; the ghost pass draws over everything, like GTNH's
     * `GL_GREATER` pass.
     */
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
        // GTNH's world units per font pixel.
        const val LABEL_SCALE = 0.026f
    }
}
