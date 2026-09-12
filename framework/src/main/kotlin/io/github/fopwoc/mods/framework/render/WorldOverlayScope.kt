package io.github.fopwoc.mods.framework.render

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.entity.RenderManager
import org.lwjgl.opengl.GL11

/**
 * Drawing primitives in world coordinates; the camera offset is applied here. Only valid inside
 * [WorldOverlay.render]. Shapes draw immediately; [label]s are queued and drawn last.
 */
@SideOnly(Side.CLIENT)
class WorldOverlayScope internal constructor(val camera: WorldCamera) {
  private val labels = ArrayList<QueuedLabel>()

  fun line(
      x1: Double,
      y1: Double,
      z1: Double,
      x2: Double,
      y2: Double,
      z2: Double,
      color: Color,
      width: Float,
  ) {
    setColor(color)
    GL11.glLineWidth(width)
    GL11.glBegin(GL11.GL_LINES)
    GL11.glVertex3d(x1 - camera.x, y1 - camera.y, z1 - camera.z)
    GL11.glVertex3d(x2 - camera.x, y2 - camera.y, z2 - camera.z)
    GL11.glEnd()
  }

  /** Twelve edges of an axis-aligned box. */
  fun boxOutline(
      minX: Double,
      minY: Double,
      minZ: Double,
      maxX: Double,
      maxY: Double,
      maxZ: Double,
      color: Color,
      width: Float,
  ) {
    val x0 = minX - camera.x
    val y0 = minY - camera.y
    val z0 = minZ - camera.z
    val x1 = maxX - camera.x
    val y1 = maxY - camera.y
    val z1 = maxZ - camera.z
    setColor(color)
    GL11.glLineWidth(width)
    val tessellator = Tessellator.instance
    tessellator.startDrawing(GL11.GL_LINES)
    for ((y, _) in listOf(y0 to 0, y1 to 1)) {
      tessellator.addVertex(x0, y, z0)
      tessellator.addVertex(x1, y, z0)
      tessellator.addVertex(x1, y, z0)
      tessellator.addVertex(x1, y, z1)
      tessellator.addVertex(x1, y, z1)
      tessellator.addVertex(x0, y, z1)
      tessellator.addVertex(x0, y, z1)
      tessellator.addVertex(x0, y, z0)
    }
    for ((x, z) in listOf(x0 to z0, x1 to z0, x1 to z1, x0 to z1)) {
      tessellator.addVertex(x, y0, z)
      tessellator.addVertex(x, y1, z)
    }
    tessellator.draw()
  }

  /**
   * Eight corner brackets of a box — reads as a handle rather than as geometry. [arm] is the
   * bracket length in blocks; [grow] pushes the brackets outward (for a hover pulse).
   */
  fun cornerBrackets(
      minX: Double,
      minY: Double,
      minZ: Double,
      maxX: Double,
      maxY: Double,
      maxZ: Double,
      color: Color,
      width: Float,
      arm: Double = 0.25,
      grow: Double = 0.0,
  ) {
    val x0 = minX - grow - camera.x
    val y0 = minY - grow - camera.y
    val z0 = minZ - grow - camera.z
    val x1 = maxX + grow - camera.x
    val y1 = maxY + grow - camera.y
    val z1 = maxZ + grow - camera.z
    val armX = arm.coerceAtMost((x1 - x0) / 2)
    val armY = arm.coerceAtMost((y1 - y0) / 2)
    val armZ = arm.coerceAtMost((z1 - z0) / 2)
    setColor(color)
    GL11.glLineWidth(width)
    GL11.glBegin(GL11.GL_LINES)
    for (x in doubleArrayOf(x0, x1)) {
      val dx = if (x == x0) armX else -armX
      for (y in doubleArrayOf(y0, y1)) {
        val dy = if (y == y0) armY else -armY
        for (z in doubleArrayOf(z0, z1)) {
          val dz = if (z == z0) armZ else -armZ
          GL11.glVertex3d(x, y, z)
          GL11.glVertex3d(x + dx, y, z)
          GL11.glVertex3d(x, y, z)
          GL11.glVertex3d(x, y + dy, z)
          GL11.glVertex3d(x, y, z)
          GL11.glVertex3d(x, y, z + dz)
        }
      }
    }
    GL11.glEnd()
  }

  /** A flat translucent box; [color]'s alpha is used as is. */
  fun filledBox(
      minX: Double,
      minY: Double,
      minZ: Double,
      maxX: Double,
      maxY: Double,
      maxZ: Double,
      color: Color,
  ) {
    val x0 = minX - camera.x
    val y0 = minY - camera.y
    val z0 = minZ - camera.z
    val x1 = maxX - camera.x
    val y1 = maxY - camera.y
    val z1 = maxZ - camera.z
    setColor(color)
    val t = Tessellator.instance
    t.startDrawingQuads()
    t.addVertex(x0, y0, z0)
    t.addVertex(x0, y1, z0)
    t.addVertex(x1, y1, z0)
    t.addVertex(x1, y0, z0)
    t.addVertex(x1, y0, z1)
    t.addVertex(x1, y1, z1)
    t.addVertex(x0, y1, z1)
    t.addVertex(x0, y0, z1)
    t.addVertex(x0, y0, z1)
    t.addVertex(x0, y1, z1)
    t.addVertex(x0, y1, z0)
    t.addVertex(x0, y0, z0)
    t.addVertex(x1, y0, z0)
    t.addVertex(x1, y1, z0)
    t.addVertex(x1, y1, z1)
    t.addVertex(x1, y0, z1)
    t.addVertex(x0, y1, z0)
    t.addVertex(x0, y1, z1)
    t.addVertex(x1, y1, z1)
    t.addVertex(x1, y1, z0)
    t.addVertex(x0, y0, z0)
    t.addVertex(x1, y0, z0)
    t.addVertex(x1, y0, z1)
    t.addVertex(x0, y0, z1)
    t.draw()
  }

  fun blockOutline(x: Int, y: Int, z: Int, color: Color, width: Float) =
      boxOutline(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0, color, width)

  /** Translucent box whose face rims glow; see [GlassSurfaces]. */
  fun glassBox(
      minX: Double,
      minY: Double,
      minZ: Double,
      maxX: Double,
      maxY: Double,
      maxZ: Double,
      color: Color,
      insideEdges: Boolean = true,
  ) =
      GlassSurfaces.box(
          minX - camera.x,
          minY - camera.y,
          minZ - camera.z,
          maxX - camera.x,
          maxY - camera.y,
          maxZ - camera.z,
          color,
          camera.eyeHeight,
          insideEdges,
      )

  fun glassSphere(
      centerX: Double,
      centerY: Double,
      centerZ: Double,
      radius: Double,
      color: Color,
      grid: GlassGrid = GlassGrid.INSIDE,
  ) =
      GlassSurfaces.sphere(
          centerX - camera.x,
          centerY - camera.y,
          centerZ - camera.z,
          radius,
          color,
          camera.eyeHeight,
          grid,
      )

  /**
   * Billboard text at a world position, drawn after all shapes and at full brightness (the font
   * goes through the lightmap, which would paint it black inside blocks). The first line takes
   * [color]; later lines use [secondaryColor].
   */
  fun label(
      x: Double,
      y: Double,
      z: Double,
      lines: List<String>,
      color: Color,
      scale: Float = 0.026f,
      secondaryColor: Color = Color(0xFFE6E6E6),
  ) {
    if (lines.isNotEmpty()) labels += QueuedLabel(x, y, z, lines, color, secondaryColor, scale)
  }

  fun label(x: Double, y: Double, z: Double, text: String, color: Color, scale: Float = 0.026f) =
      label(x, y, z, listOf(text), color, scale)

  internal fun drawQueuedLabels(minecraft: Minecraft) {
    if (labels.isEmpty()) return
    val renderManager = RenderManager.instance
    val font = minecraft.fontRenderer
    val lineHeight = font.FONT_HEIGHT + 1

    GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)
    GL11.glDisable(GL11.GL_LIGHTING)
    GL11.glDisable(GL11.GL_DEPTH_TEST)
    GL11.glDepthMask(false)
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    val brightnessX = OpenGlHelper.lastBrightnessX
    val brightnessY = OpenGlHelper.lastBrightnessY
    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f)

    labels.forEach { label ->
      GL11.glPushMatrix()
      GL11.glTranslated(label.x - camera.x, label.y - camera.y, label.z - camera.z)
      GL11.glNormal3f(0f, 1f, 0f)
      GL11.glRotatef(-renderManager.playerViewY, 0f, 1f, 0f)
      GL11.glRotatef(renderManager.playerViewX, 1f, 0f, 0f)
      GL11.glScalef(-label.scale, -label.scale, label.scale)
      GL11.glColor4f(1f, 1f, 1f, 1f)
      val totalHeight = label.lines.size * lineHeight
      label.lines.forEachIndexed { index, line ->
        val argb = (if (index == 0) label.color else label.secondaryColor).argbInt
        font.drawStringWithShadow(
            line,
            -font.getStringWidth(line) / 2,
            -totalHeight + index * lineHeight,
            argb,
        )
      }
      GL11.glPopMatrix()
    }

    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY)
    GL11.glPopAttrib()
    labels.clear()
  }

  private fun setColor(color: Color) {
    GL11.glColor4f(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
  }

  private class QueuedLabel(
      val x: Double,
      val y: Double,
      val z: Double,
      val lines: List<String>,
      val color: Color,
      val secondaryColor: Color,
      val scale: Float,
  )
}
