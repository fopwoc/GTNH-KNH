package io.github.fopwoc.mods.framework.render

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.Minecraft
import net.minecraft.entity.Entity
import org.lwjgl.opengl.GL11

/**
 * Entry point for drawing in the world from `RenderWorldLastEvent`: sets up the overlay GL state
 * (no texture, no lighting, no depth, blending on), hands out a [WorldOverlayScope] with the
 * primitives, and draws the queued labels after the shapes so text is never covered by them.
 *
 * ```kotlin
 * @SubscribeEvent
 * fun onRenderWorld(event: RenderWorldLastEvent) =
 *     WorldOverlay.render(event.partialTicks) {
 *       glassBox(x, y, z, x + 3.0, y + 2.0, z + 3.0, color)
 *       label(x + 1.5, y + 2.5, z + 1.5, listOf("3 × 2 × 3"), color)
 *     }
 * ```
 */
@SideOnly(Side.CLIENT)
object WorldOverlay {
  fun render(
      partialTicks: Float,
      viewer: Entity? = Minecraft.getMinecraft().let { it.renderViewEntity ?: it.thePlayer },
      draw: WorldOverlayScope.() -> Unit,
  ) {
    val entity = viewer ?: return
    val scope = WorldOverlayScope(WorldCamera.of(entity, partialTicks))

    GL11.glPushAttrib(
        GL11.GL_ENABLE_BIT or
            GL11.GL_LINE_BIT or
            GL11.GL_COLOR_BUFFER_BIT or
            GL11.GL_DEPTH_BUFFER_BIT or
            GL11.GL_POLYGON_BIT
    )
    GL11.glPushMatrix()
    GL11.glDisable(GL11.GL_TEXTURE_2D)
    GL11.glDisable(GL11.GL_LIGHTING)
    GL11.glDisable(GL11.GL_DEPTH_TEST)
    GL11.glDisable(GL11.GL_CULL_FACE)
    GL11.glDisable(GL11.GL_ALPHA_TEST)
    GL11.glDepthMask(false)
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    try {
      scope.draw()
    } finally {
      GL11.glDepthMask(true)
      GL11.glPopMatrix()
      GL11.glPopAttrib()
    }
    scope.drawQueuedLabels(Minecraft.getMinecraft())
  }
}
