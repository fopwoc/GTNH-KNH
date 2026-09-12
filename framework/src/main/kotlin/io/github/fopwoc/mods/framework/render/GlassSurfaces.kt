package io.github.fopwoc.mods.framework.render

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
 * Translucent "glass" volumes that sit in the world: depth-tested so terrain hides them, with the
 * hidden parts ghosted in a second pass so the shape still reads through walls. Surfaces are faint
 * where they face the camera and opaque towards their edges, so the outline is the surface itself —
 * no wire lines needed. Fixed-function GL only.
 */
@SideOnly(Side.CLIENT)
internal object GlassSurfaces {
  private const val FILL_ALPHA = 0.05f
  private const val RIM_ALPHA = 0.7f
  private const val BOX_RIM = 0.12
  private const val LIGHT_X = 0.35f
  private const val LIGHT_Y = 0.8f
  private const val LIGHT_Z = 0.45f

  /** Camera-relative sphere: the camera is at the origin. */
  fun sphere(originX: Double, originY: Double, originZ: Double, radius: Double, color: Color) {
    val slices = (24 + radius * 2).toInt().coerceIn(24, 96)
    val stacks = slices / 2
    glass { alphaScale ->
      val tessellator = Tessellator.instance
      tessellator.startDrawingQuads()
      for (stack in 0 until stacks) {
        val phi0 = PI * stack / stacks
        val phi1 = PI * (stack + 1) / stacks
        for (slice in 0 until slices) {
          val theta0 = 2 * PI * slice / slices
          val theta1 = 2 * PI * (slice + 1) / slices
          sphereVertex(
              tessellator,
              originX,
              originY,
              originZ,
              radius,
              phi0,
              theta0,
              color,
              alphaScale,
          )
          sphereVertex(
              tessellator,
              originX,
              originY,
              originZ,
              radius,
              phi1,
              theta0,
              color,
              alphaScale,
          )
          sphereVertex(
              tessellator,
              originX,
              originY,
              originZ,
              radius,
              phi1,
              theta1,
              color,
              alphaScale,
          )
          sphereVertex(
              tessellator,
              originX,
              originY,
              originZ,
              radius,
              phi0,
              theta1,
              color,
              alphaScale,
          )
        }
      }
      tessellator.draw()
    }
  }

  /** Camera-relative axis-aligned box. Each face has a bright rim fading into a faint centre. */
  fun box(
      minX: Double,
      minY: Double,
      minZ: Double,
      maxX: Double,
      maxY: Double,
      maxZ: Double,
      color: Color,
  ) {
    glass { alphaScale ->
      val tessellator = Tessellator.instance
      tessellator.startDrawingQuads()
      face(
          tessellator,
          color,
          alphaScale,
          0f,
          0f,
          -1f,
          minX,
          maxY,
          minZ,
          maxX,
          maxY,
          minZ,
          maxX,
          minY,
          minZ,
          minX,
          minY,
          minZ,
      )
      face(
          tessellator,
          color,
          alphaScale,
          0f,
          0f,
          1f,
          minX,
          minY,
          maxZ,
          maxX,
          minY,
          maxZ,
          maxX,
          maxY,
          maxZ,
          minX,
          maxY,
          maxZ,
      )
      face(
          tessellator,
          color,
          alphaScale,
          -1f,
          0f,
          0f,
          minX,
          minY,
          minZ,
          minX,
          minY,
          maxZ,
          minX,
          maxY,
          maxZ,
          minX,
          maxY,
          minZ,
      )
      face(
          tessellator,
          color,
          alphaScale,
          1f,
          0f,
          0f,
          maxX,
          maxY,
          minZ,
          maxX,
          maxY,
          maxZ,
          maxX,
          minY,
          maxZ,
          maxX,
          minY,
          minZ,
      )
      face(
          tessellator,
          color,
          alphaScale,
          0f,
          -1f,
          0f,
          minX,
          minY,
          minZ,
          maxX,
          minY,
          minZ,
          maxX,
          minY,
          maxZ,
          minX,
          minY,
          maxZ,
      )
      face(
          tessellator,
          color,
          alphaScale,
          0f,
          1f,
          0f,
          minX,
          maxY,
          maxZ,
          maxX,
          maxY,
          maxZ,
          maxX,
          maxY,
          minZ,
          minX,
          maxY,
          minZ,
      )
      tessellator.draw()
    }
  }

  private inline fun glass(draw: (alphaScale: Float) -> Unit) {
    GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_DEPTH_BUFFER_BIT or GL11.GL_POLYGON_BIT)
    GL11.glDisable(GL11.GL_DEPTH_TEST)
    GL11.glDepthMask(false)
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    GL11.glDisable(GL11.GL_CULL_FACE)
    GL11.glShadeModel(GL11.GL_SMOOTH)
    draw(1f)
    GL11.glPopAttrib()
  }

  private fun sphereVertex(
      tessellator: Tessellator,
      originX: Double,
      originY: Double,
      originZ: Double,
      radius: Double,
      phi: Double,
      theta: Double,
      color: Color,
      alphaScale: Float,
  ) {
    val normalX = (sin(phi) * cos(theta)).toFloat()
    val normalY = cos(phi).toFloat()
    val normalZ = (sin(phi) * sin(theta)).toFloat()
    val x = originX + normalX * radius
    val y = originY + normalY * radius
    val z = originZ + normalZ * radius
    val facing = facing(normalX, normalY, normalZ, x, y, z)
    val rim = (1f - facing) * (1f - facing)
    val alpha = (FILL_ALPHA + (RIM_ALPHA - FILL_ALPHA) * rim) * alphaScale
    emit(tessellator, color, light(normalX, normalY, normalZ), alpha, x, y, z)
  }

  /**
   * One box face as a rim strip plus centre: outer vertices carry the rim alpha, inner ones the
   * fill alpha, so the edges glow and the middle stays glassy. Corners are the four points in
   * winding order; the rim is inset along the face's two edge directions.
   */
  private fun face(
      tessellator: Tessellator,
      color: Color,
      alphaScale: Float,
      normalX: Float,
      normalY: Float,
      normalZ: Float,
      x0: Double,
      y0: Double,
      z0: Double,
      x1: Double,
      y1: Double,
      z1: Double,
      x2: Double,
      y2: Double,
      z2: Double,
      x3: Double,
      y3: Double,
      z3: Double,
  ) {
    val centerX = (x0 + x2) / 2
    val centerY = (y0 + y2) / 2
    val centerZ = (z0 + z2) / 2
    // Faces seen edge-on are brighter, like the sphere rim; faces seen head-on stay faint.
    val facing = facing(normalX, normalY, normalZ, centerX, centerY, centerZ)
    val faceScale = 0.6f + 0.4f * (1f - facing)
    val light = light(normalX, normalY, normalZ)
    val rimAlpha = RIM_ALPHA * faceScale * alphaScale
    val fillAlpha = FILL_ALPHA * alphaScale

    val outer =
        arrayOf(
            doubleArrayOf(x0, y0, z0),
            doubleArrayOf(x1, y1, z1),
            doubleArrayOf(x2, y2, z2),
            doubleArrayOf(x3, y3, z3),
        )
    val inner = Array(4) { index -> inset(outer, index) }

    for (index in 0 until 4) {
      val next = (index + 1) % 4
      val o0 = outer[index]
      val o1 = outer[next]
      val i1 = inner[next]
      val i0 = inner[index]
      emit(tessellator, color, light, rimAlpha, o0[0], o0[1], o0[2])
      emit(tessellator, color, light, rimAlpha, o1[0], o1[1], o1[2])
      emit(tessellator, color, light, fillAlpha, i1[0], i1[1], i1[2])
      emit(tessellator, color, light, fillAlpha, i0[0], i0[1], i0[2])
    }
    emit(tessellator, color, light, fillAlpha, inner[0][0], inner[0][1], inner[0][2])
    emit(tessellator, color, light, fillAlpha, inner[1][0], inner[1][1], inner[1][2])
    emit(tessellator, color, light, fillAlpha, inner[2][0], inner[2][1], inner[2][2])
    emit(tessellator, color, light, fillAlpha, inner[3][0], inner[3][1], inner[3][2])
  }

  /** Moves corner [index] towards the face centre by [BOX_RIM] along each of its two edges. */
  private fun inset(outer: Array<DoubleArray>, index: Int): DoubleArray {
    val corner = outer[index]
    val previous = outer[(index + 3) % 4]
    val next = outer[(index + 1) % 4]
    val result = DoubleArray(3)
    for (axis in 0 until 3) {
      val toNext = next[axis] - corner[axis]
      val toPrevious = previous[axis] - corner[axis]
      result[axis] = corner[axis] + step(toNext) + step(toPrevious)
    }
    return result
  }

  private fun step(delta: Double): Double =
      when {
        delta > BOX_RIM * 2 -> BOX_RIM
        delta < -BOX_RIM * 2 -> -BOX_RIM
        else -> delta / 2
      }

  private fun facing(nx: Float, ny: Float, nz: Float, x: Double, y: Double, z: Double): Float {
    val length = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-4)
    return abs((nx * -x + ny * -y + nz * -z) / length).toFloat()
  }

  private fun light(nx: Float, ny: Float, nz: Float): Float =
      0.7f + 0.3f * maxOf(0f, nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z)

  private fun emit(
      tessellator: Tessellator,
      color: Color,
      light: Float,
      alpha: Float,
      x: Double,
      y: Double,
      z: Double,
  ) {
    tessellator.setColorRGBA_F(
        color.red / 255f * light,
        color.green / 255f * light,
        color.blue / 255f * light,
        alpha,
    )
    tessellator.addVertex(x, y, z)
  }
}
