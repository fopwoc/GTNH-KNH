package io.github.fopwoc.mods.framework.render

import net.minecraft.world.phys.Vec3

/** What a custom world shape emits, in world coordinates. */
interface WorldPrimitives {
    fun addQuad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, argb: Int)

    fun addLine(from: Vec3, to: Vec3, argb: Int, width: Float)
}

// Overlay shapes in the world for the frame being drawn, only inside a WorldOverlays callback:
// 26.x gizmos draw through the game's graphics backend; 1.21.1 draws them itself.
/*? if >=26 {*/
typealias WorldShapes = ModernWorldShapes
/*?} else {*/
/*typealias WorldShapes = LegacyWorldShapes
 *//*?}*/
