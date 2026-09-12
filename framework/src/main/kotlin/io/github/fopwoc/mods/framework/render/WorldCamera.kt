package io.github.fopwoc.mods.framework.render

import net.minecraft.entity.Entity

/**
 * Where the world is drawn from this frame. `RenderWorldLastEvent` is translated so the render view
 * entity's interpolated position is the origin; everything drawn must subtract [x]/[y]/[z]. The
 * view entity may be a detached camera (freecam), not the player.
 */
class WorldCamera(val x: Double, val y: Double, val z: Double, val eyeHeight: Double) {
  val eyeX: Double
    get() = x

  val eyeY: Double
    get() = y + eyeHeight

  val eyeZ: Double
    get() = z

  companion object {
    fun of(viewer: Entity, partialTicks: Float): WorldCamera {
      val partial = partialTicks.toDouble()
      return WorldCamera(
          x = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partial,
          y = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partial,
          z = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partial,
          eyeHeight = viewer.eyeHeight.toDouble(),
      )
    }
  }
}
