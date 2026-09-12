package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import net.minecraft.client.renderer.Tessellator
import org.lwjgl.opengl.GL11

/**
 * A translucent sphere that sits in the world: depth-tested so terrain hides it, more opaque at the
 * silhouette than in the middle (a fresnel-ish alpha), lit faintly from above so it reads as a
 * solid. Drawn with the fixed-function pipeline only, so it works under any renderer.
 */
@SideOnly(Side.CLIENT)
object SphereSurfaceRenderer {
  private const val MIN_ALPHA = 0.06f
  private const val EDGE_ALPHA = 0.55f
  private const val LIGHT_Y = 0.8f
  private const val LIGHT_X = 0.35f
  private const val LIGHT_Z = 0.45f

  /** [originX/Y/Z] are camera-relative; the camera is therefore at the origin. */
  fun draw(originX: Double, originY: Double, originZ: Double, radius: Double, color: Color) {
    val slices = (24 + radius * 2).toInt().coerceIn(24, 96)
    val stacks = slices / 2
    val distance = sqrt(originX * originX + originY * originY + originZ * originZ)
    val cameraInside = distance < radius

    GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_DEPTH_BUFFER_BIT or GL11.GL_POLYGON_BIT)
    GL11.glEnable(GL11.GL_DEPTH_TEST)
    GL11.glDepthFunc(GL11.GL_LEQUAL)
    GL11.glDepthMask(false)
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    GL11.glEnable(GL11.GL_CULL_FACE)
    // From outside only the near hemisphere is wanted; from inside, the far one.
    GL11.glCullFace(if (cameraInside) GL11.GL_FRONT else GL11.GL_BACK)
    GL11.glShadeModel(GL11.GL_SMOOTH)

    val red = color.red / 255f
    val green = color.green / 255f
    val blue = color.blue / 255f
    val tessellator = Tessellator.instance
    tessellator.startDrawingQuads()
    for (stack in 0 until stacks) {
      val phi0 = PI * stack / stacks
      val phi1 = PI * (stack + 1) / stacks
      for (slice in 0 until slices) {
        val theta0 = 2 * PI * slice / slices
        val theta1 = 2 * PI * (slice + 1) / slices
        // Counter-clockwise seen from outside so GL_BACK culling keeps the outer face.
        vertex(tessellator, originX, originY, originZ, radius, phi0, theta0, red, green, blue)
        vertex(tessellator, originX, originY, originZ, radius, phi1, theta0, red, green, blue)
        vertex(tessellator, originX, originY, originZ, radius, phi1, theta1, red, green, blue)
        vertex(tessellator, originX, originY, originZ, radius, phi0, theta1, red, green, blue)
      }
    }
    tessellator.draw()
    GL11.glPopAttrib()
  }

  private fun vertex(
      tessellator: Tessellator,
      originX: Double,
      originY: Double,
      originZ: Double,
      radius: Double,
      phi: Double,
      theta: Double,
      red: Float,
      green: Float,
      blue: Float,
  ) {
    val normalX = (sin(phi) * cos(theta)).toFloat()
    val normalY = cos(phi).toFloat()
    val normalZ = (sin(phi) * sin(theta)).toFloat()
    val x = originX + normalX * radius
    val y = originY + normalY * radius
    val z = originZ + normalZ * radius

    // View vector from the vertex to the camera at the origin.
    val length = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-4)
    val facing = abs((normalX * -x + normalY * -y + normalZ * -z) / length).toFloat()
    val rim = (1f - facing) * (1f - facing)
    val alpha = MIN_ALPHA + (EDGE_ALPHA - MIN_ALPHA) * rim
    val light = 0.7f + 0.3f * maxOf(0f, normalX * LIGHT_X + normalY * LIGHT_Y + normalZ * LIGHT_Z)

    tessellator.setColorRGBA_F(red * light, green * light, blue * light, alpha)
    tessellator.addVertex(x, y, z)
  }
}
