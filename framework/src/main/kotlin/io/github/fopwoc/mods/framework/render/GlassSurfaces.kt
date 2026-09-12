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
  // Seen from inside, every point faces the eye and the rim vanishes; keep the shell readable.
  private const val INSIDE_FILL_ALPHA = 0.22f
  private const val GRID_ALPHA = 0.28f
  private const val GRID_STRONG_ALPHA = 0.6f
  private const val RING_ALPHA = 0.9f
  private const val RIM_ALPHA = 0.7f
  private const val BOX_RIM = 0.12
  private const val LIGHT_X = 0.35f
  private const val LIGHT_Y = 0.8f
  private const val LIGHT_Z = 0.45f

  /** Camera-relative sphere; the eye sits at (0, [eyeY], 0). */
  fun sphere(
      originX: Double,
      originY: Double,
      originZ: Double,
      radius: Double,
      color: Color,
      eyeY: Double,
      grid: GlassGrid,
  ) {
    val slices = (24 + radius * 2).toInt().coerceIn(24, 64)
    val stacks = slices / 2
    val eyeDistance =
        sqrt(originX * originX + (originY - eyeY) * (originY - eyeY) + originZ * originZ)
    val fillAlpha = if (eyeDistance < radius) INSIDE_FILL_ALPHA else FILL_ALPHA
    glass { alphaScale ->
      val tessellator = Tessellator.instance
      // One latitude band per batch: a whole sphere in one Tessellator batch overflows the
      // patched tessellators GTNH ships and comes out as confetti.
      for (stack in 0 until stacks) {
        val phi0 = PI * stack / stacks
        val phi1 = PI * (stack + 1) / stacks
        tessellator.startDrawingQuads()
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
              eyeY,
              fillAlpha,
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
              eyeY,
              fillAlpha,
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
              eyeY,
              fillAlpha,
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
              eyeY,
              fillAlpha,
          )
        }
        tessellator.draw()
      }
      if (grid == GlassGrid.ALWAYS || grid == GlassGrid.INSIDE && eyeDistance < radius) {
        sphereGrid(originX, originY, originZ, radius, slices, stacks, color, eyeY)
      }
    }
  }

  /**
   * A faint latitude/longitude grid (equator and four meridians stronger) so the curvature reads,
   * and a bright ring where the shell crosses eye height — also the layer to build when placing the
   * sphere block by block.
   */
  private fun sphereGrid(
      originX: Double,
      originY: Double,
      originZ: Double,
      radius: Double,
      slices: Int,
      stacks: Int,
      color: Color,
      eyeY: Double,
  ) {
    GL11.glLineWidth(1.5f)
    val red = color.red / 255f
    val green = color.green / 255f
    val blue = color.blue / 255f
    // Latitude rings every other stack.
    for (stack in 2 until stacks step 2) {
      val phi = PI * stack / stacks
      val alpha = if (stack == stacks / 2) GRID_STRONG_ALPHA else GRID_ALPHA
      GL11.glColor4f(red, green, blue, alpha)
      GL11.glBegin(GL11.GL_LINE_LOOP)
      for (slice in 0 until slices) {
        val theta = 2 * PI * slice / slices
        GL11.glVertex3d(
            originX + sin(phi) * cos(theta) * radius,
            originY + cos(phi) * radius,
            originZ + sin(phi) * sin(theta) * radius,
        )
      }
      GL11.glEnd()
    }
    // Meridians every other slice; the four cardinal ones stronger.
    for (slice in 0 until slices step 2) {
      val theta = 2 * PI * slice / slices
      val cardinal = slice % (slices / 4) == 0
      GL11.glColor4f(red, green, blue, if (cardinal) GRID_STRONG_ALPHA else GRID_ALPHA)
      GL11.glBegin(GL11.GL_LINE_STRIP)
      for (stack in 0..stacks) {
        val phi = PI * stack / stacks
        GL11.glVertex3d(
            originX + sin(phi) * cos(theta) * radius,
            originY + cos(phi) * radius,
            originZ + sin(phi) * sin(theta) * radius,
        )
      }
      GL11.glEnd()
    }
    // Ring at eye height.
    val dy = eyeY - originY
    val ringRadius = sqrt((radius * radius - dy * dy).coerceAtLeast(0.0))
    if (ringRadius > 0.0) {
      GL11.glLineWidth(2.5f)
      GL11.glColor4f(red, green, blue, RING_ALPHA)
      GL11.glBegin(GL11.GL_LINE_LOOP)
      val segments = slices * 2
      for (index in 0 until segments) {
        val theta = 2 * PI * index / segments
        GL11.glVertex3d(originX + cos(theta) * ringRadius, eyeY, originZ + sin(theta) * ringRadius)
      }
      GL11.glEnd()
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
      eyeY: Double,
      insideEdges: Boolean,
  ) {
    val inside = 0.0 in minX..maxX && eyeY in minY..maxY && 0.0 in minZ..maxZ
    glass { alphaScale ->
      val tessellator = Tessellator.instance
      tessellator.startDrawingQuads()
      face(
          tessellator,
          color,
          alphaScale,
          eyeY,
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
          eyeY,
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
          eyeY,
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
          eyeY,
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
          eyeY,
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
          eyeY,
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
      // From inside, the faces face the eye and fade out; the twelve edges keep the box readable.
      if (insideEdges && inside) {
        boxEdges(minX, minY, minZ, maxX, maxY, maxZ, color)
      }
    }
  }

  private fun boxEdges(
      minX: Double,
      minY: Double,
      minZ: Double,
      maxX: Double,
      maxY: Double,
      maxZ: Double,
      color: Color,
  ) {
    GL11.glLineWidth(2f)
    GL11.glColor4f(color.red / 255f, color.green / 255f, color.blue / 255f, GRID_STRONG_ALPHA)
    GL11.glBegin(GL11.GL_LINES)
    for (y in doubleArrayOf(minY, maxY)) {
      GL11.glVertex3d(minX, y, minZ)
      GL11.glVertex3d(maxX, y, minZ)
      GL11.glVertex3d(maxX, y, minZ)
      GL11.glVertex3d(maxX, y, maxZ)
      GL11.glVertex3d(maxX, y, maxZ)
      GL11.glVertex3d(minX, y, maxZ)
      GL11.glVertex3d(minX, y, maxZ)
      GL11.glVertex3d(minX, y, minZ)
    }
    for ((x, z) in listOf(minX to minZ, maxX to minZ, maxX to maxZ, minX to maxZ)) {
      GL11.glVertex3d(x, minY, z)
      GL11.glVertex3d(x, maxY, z)
    }
    GL11.glEnd()
  }

  private inline fun glass(draw: (alphaScale: Float) -> Unit) {
    GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_DEPTH_BUFFER_BIT or GL11.GL_POLYGON_BIT)
    GL11.glDisable(GL11.GL_DEPTH_TEST)
    GL11.glDepthMask(false)
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    GL11.glDisable(GL11.GL_CULL_FACE)
    GL11.glDisable(GL11.GL_TEXTURE_2D)
    // Minecraft renders the world with an alpha test at 0.1; the glass fill is well below that.
    GL11.glDisable(GL11.GL_ALPHA_TEST)
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
      eyeY: Double,
      fillAlpha: Float,
  ) {
    val normalX = (sin(phi) * cos(theta)).toFloat()
    val normalY = cos(phi).toFloat()
    val normalZ = (sin(phi) * sin(theta)).toFloat()
    val x = originX + normalX * radius
    val y = originY + normalY * radius
    val z = originZ + normalZ * radius
    val facing = facing(normalX, normalY, normalZ, x, y - eyeY, z)
    val rim = (1f - facing) * (1f - facing)
    // Nearer shell brighter than the far side, so an off-centre viewer feels which wall is close.
    val dx = x
    val dy = y - eyeY
    val dz = z
    val proximity =
        (1.0 - sqrt(dx * dx + dy * dy + dz * dz) / (2.0 * radius)).coerceIn(0.35, 1.0).toFloat()
    val alpha = (fillAlpha * proximity + (RIM_ALPHA - fillAlpha) * rim) * alphaScale
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
      eyeY: Double,
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
    val facing = facing(normalX, normalY, normalZ, centerX, centerY - eyeY, centerZ)
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
