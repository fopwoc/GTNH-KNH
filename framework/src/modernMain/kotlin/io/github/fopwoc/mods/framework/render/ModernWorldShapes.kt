/*? if >=26 {*/
// 26.x only: gizmos; LegacyWorldShapes draws the same shapes on 1.21.1.
package io.github.fopwoc.mods.framework.render

import net.minecraft.client.Minecraft
import net.minecraft.gizmos.Gizmo
import net.minecraft.gizmos.GizmoPrimitives
import net.minecraft.gizmos.GizmoProperties
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.SimpleGizmoCollector
import net.minecraft.gizmos.TextGizmo
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * World overlay shapes as Minecraft 26.x gizmos, which draw through the game's graphics backend,
 * including Vulkan. Shapes are depth-tested against terrain unless `onTop`.
 */
object ModernWorldShapes {
    fun line(from: Vec3, to: Vec3, argb: Int, width: Float, onTop: Boolean) =
        Gizmos.line(from, to, argb, width).layer(onTop)

    fun box(box: AABB, argb: Int, onTop: Boolean) =
        Gizmos.cuboid(box, GizmoStyle.fill(argb)).layer(onTop)

    fun boxOutline(box: AABB, argb: Int, width: Float, onTop: Boolean) =
        Gizmos.cuboid(box, GizmoStyle.stroke(argb, width)).layer(onTop)

    /** Camera-facing text centred on [at], over everything; [scale] is world units per pixel. */
    fun text(text: String, at: Vec3, argb: Int, scale: Float) {
        Gizmos.billboardText(
                text,
                at,
                TextGizmo.Style.forColorAndCentered(argb).withScale(scale * GIZMO_TEXT_SCALE),
            )
            .setAlwaysOnTop()
    }

    /** A shape of quads and lines; [draw] gets the game's fade-out multiplier for alpha. */
    fun custom(onTop: Boolean, draw: WorldPrimitives.(alphaMultiplier: Float) -> Unit) =
        Gizmos.addGizmo(Gizmo { primitives, alpha -> Primitives(primitives).draw(alpha) })
            .layer(onTop)

    internal fun frame(draw: () -> Unit) {
        val collector = SimpleGizmoCollector()
        Gizmos.withCollector(collector).use { draw() }
        Minecraft.getInstance().levelRenderer.addMainThreadGizmos(collector.drainGizmos())
    }

    private fun GizmoProperties.layer(onTop: Boolean) {
        if (onTop) setAlwaysOnTop()
    }

    private class Primitives(private val target: GizmoPrimitives) : WorldPrimitives {
        override fun addQuad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, argb: Int) =
            target.addQuad(a, b, c, d, argb)

        override fun addLine(from: Vec3, to: Vec3, argb: Int, width: Float) =
            target.addLine(from, to, argb, width)
    }

    // The gizmo renderer divides text scale by 16.
    private const val GIZMO_TEXT_SCALE = 16f
}
/*?}*/
