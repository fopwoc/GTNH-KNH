package io.github.fopwoc.mods.hotspot.client.overlay

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.hotspot.client.format.TimingFormat
import io.github.fopwoc.mods.hotspot.client.profile.ChunkRef
import io.github.fopwoc.mods.hotspot.client.profile.ProfileStore
import io.github.fopwoc.mods.hotspot.client.profile.TileEntityRef
import io.github.fopwoc.mods.hotspot.config.HotspotConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraftforge.client.event.RenderWorldLastEvent
import org.lwjgl.opengl.GL11

/**
 * Draws what the menu picked: highlighted chunks as tinted columns, highlighted tile entities as
 * boxes, both with ms labels. Colour is the item's share of the heaviest highlighted item, so the
 * worst one is always red.
 */
@SideOnly(Side.CLIENT)
object HotspotOverlayRenderer {
  @SubscribeEvent
  fun onRenderWorld(event: RenderWorldLastEvent) {
    val minecraft = Minecraft.getMinecraft()
    val world = minecraft.theWorld ?: return
    val viewer = minecraft.renderViewEntity ?: minecraft.thePlayer ?: return
    val dimensionId = world.provider.dimensionId

    val chunks = ProfileStore.highlightedChunks(dimensionId)
    val tileEntities = ProfileStore.highlightedTileEntities(dimensionId)
    if (chunks.isEmpty() && tileEntities.isEmpty()) {
      return
    }

    val partial = event.partialTicks.toDouble()
    val cameraX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partial
    val cameraY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partial
    val cameraZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partial
    val labelRange = HotspotConfig.labelDistance.toDouble()

    GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_LINE_BIT or GL11.GL_COLOR_BUFFER_BIT)
    GL11.glPushMatrix()
    GL11.glDisable(GL11.GL_TEXTURE_2D)
    GL11.glDisable(GL11.GL_LIGHTING)
    GL11.glDisable(GL11.GL_DEPTH_TEST)
    GL11.glDepthMask(false)
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

    val chunkMaxMs = chunks.maxOfOrNull { ProfileStore.chunk(it)?.totalMs ?: 0.0 } ?: 0.0
    val chunkLabels = ArrayList<Label>()
    for (ref in chunks) {
      val profile = ProfileStore.chunk(ref) ?: continue
      val heat = HeatScale.color(if (chunkMaxMs > 0.0) profile.totalMs / chunkMaxMs else 0.0)
      drawChunkColumn(ref, heat, cameraX, cameraY, cameraZ)
      chunkLabels +=
          Label(
              text =
                  listOf(
                      TimingFormat.ms(profile.totalMs),
                      TimingFormat.chunk(ref.chunkX, ref.chunkZ),
                  ),
              x = ref.chunkX * 16.0 + 8.0,
              y = cameraY + 2.0,
              z = ref.chunkZ * 16.0 + 8.0,
              color = heat,
          )
    }

    val tileEntityMaxMs = tileEntities.maxOfOrNull { ProfileStore.tileEntity(it)?.ms ?: 0.0 } ?: 0.0
    val tileEntityLabels = ArrayList<Label>()
    for (ref in tileEntities) {
      val profile = ProfileStore.tileEntity(ref) ?: continue
      val heat = HeatScale.color(if (tileEntityMaxMs > 0.0) profile.ms / tileEntityMaxMs else 0.0)
      drawTileEntityBox(ref, heat, cameraX, cameraY, cameraZ)
      val distance = distance(ref.x + 0.5, ref.y + 0.5, ref.z + 0.5, cameraX, cameraY, cameraZ)
      if (distance <= labelRange) {
        val lines = mutableListOf(TimingFormat.ms(profile.ms), profile.name)
        if (HotspotConfig.showTechnicalNames && profile.className.isNotEmpty()) {
          lines += profile.className
        }
        tileEntityLabels +=
            Label(
                text = lines,
                x = ref.x + 0.5,
                y = ref.y + 1.35,
                z = ref.z + 0.5,
                color = heat,
            )
      }
    }

    GL11.glPopMatrix()
    GL11.glPopAttrib()

    if (HotspotConfig.showLabels) {
      chunkLabels.forEach {
        drawWorldLabel(minecraft, it, cameraX, cameraY, cameraZ, scale = 0.04f)
      }
      tileEntityLabels.forEach {
        drawWorldLabel(minecraft, it, cameraX, cameraY, cameraZ, scale = 0.02f)
      }
    }
  }

  private class Label(
      val text: List<String>,
      val x: Double,
      val y: Double,
      val z: Double,
      val color: Color,
  )

  private fun drawChunkColumn(
      ref: ChunkRef,
      heat: Color,
      cameraX: Double,
      cameraY: Double,
      cameraZ: Double,
  ) {
    val minX = ref.chunkX * 16.0 - cameraX
    val minZ = ref.chunkZ * 16.0 - cameraZ
    val maxX = minX + 16.0
    val maxZ = minZ + 16.0
    val minY = 0.0 - cameraY
    val maxY = HotspotConfig.chunkColumnHeight.toDouble() - cameraY
    val fillAlpha = (HotspotConfig.chunkFillAlpha * 255).toInt().coerceIn(0, 255)

    setColor(heat, fillAlpha)
    val tessellator = Tessellator.instance
    tessellator.startDrawingQuads()
    // Four walls; no top or bottom so the column reads as an outline from above.
    tessellator.addVertex(minX, minY, minZ)
    tessellator.addVertex(minX, maxY, minZ)
    tessellator.addVertex(maxX, maxY, minZ)
    tessellator.addVertex(maxX, minY, minZ)

    tessellator.addVertex(maxX, minY, maxZ)
    tessellator.addVertex(maxX, maxY, maxZ)
    tessellator.addVertex(minX, maxY, maxZ)
    tessellator.addVertex(minX, minY, maxZ)

    tessellator.addVertex(minX, minY, maxZ)
    tessellator.addVertex(minX, maxY, maxZ)
    tessellator.addVertex(minX, maxY, minZ)
    tessellator.addVertex(minX, minY, minZ)

    tessellator.addVertex(maxX, minY, minZ)
    tessellator.addVertex(maxX, maxY, minZ)
    tessellator.addVertex(maxX, maxY, maxZ)
    tessellator.addVertex(maxX, minY, maxZ)
    tessellator.draw()

    setColor(heat, 200)
    GL11.glLineWidth(2.0f)
    tessellator.startDrawing(GL11.GL_LINES)
    for ((x, z) in listOf(minX to minZ, maxX to minZ, maxX to maxZ, minX to maxZ)) {
      tessellator.addVertex(x, minY, z)
      tessellator.addVertex(x, maxY, z)
    }
    tessellator.draw()
  }

  private fun drawTileEntityBox(
      ref: TileEntityRef,
      heat: Color,
      cameraX: Double,
      cameraY: Double,
      cameraZ: Double,
  ) {
    val minX = ref.x - cameraX
    val minY = ref.y - cameraY
    val minZ = ref.z - cameraZ
    val maxX = minX + 1.0
    val maxY = minY + 1.0
    val maxZ = minZ + 1.0
    val tessellator = Tessellator.instance

    setColor(heat, 70)
    tessellator.startDrawingQuads()
    tessellator.addVertex(minX, minY, minZ)
    tessellator.addVertex(minX, maxY, minZ)
    tessellator.addVertex(maxX, maxY, minZ)
    tessellator.addVertex(maxX, minY, minZ)
    tessellator.addVertex(maxX, minY, maxZ)
    tessellator.addVertex(maxX, maxY, maxZ)
    tessellator.addVertex(minX, maxY, maxZ)
    tessellator.addVertex(minX, minY, maxZ)
    tessellator.addVertex(minX, minY, maxZ)
    tessellator.addVertex(minX, maxY, maxZ)
    tessellator.addVertex(minX, maxY, minZ)
    tessellator.addVertex(minX, minY, minZ)
    tessellator.addVertex(maxX, minY, minZ)
    tessellator.addVertex(maxX, maxY, minZ)
    tessellator.addVertex(maxX, maxY, maxZ)
    tessellator.addVertex(maxX, minY, maxZ)
    tessellator.addVertex(minX, maxY, minZ)
    tessellator.addVertex(minX, maxY, maxZ)
    tessellator.addVertex(maxX, maxY, maxZ)
    tessellator.addVertex(maxX, maxY, minZ)
    tessellator.addVertex(minX, minY, minZ)
    tessellator.addVertex(maxX, minY, minZ)
    tessellator.addVertex(maxX, minY, maxZ)
    tessellator.addVertex(minX, minY, maxZ)
    tessellator.draw()

    setColor(heat, 255)
    GL11.glLineWidth(2.5f)
    tessellator.startDrawing(GL11.GL_LINES)
    val corners =
        listOf(
            Triple(minX, minY, minZ),
            Triple(maxX, minY, minZ),
            Triple(maxX, minY, maxZ),
            Triple(minX, minY, maxZ),
        )
    corners.forEachIndexed { index, (x, y, z) ->
      val (nextX, _, nextZ) = corners[(index + 1) % corners.size]
      tessellator.addVertex(x, y, z)
      tessellator.addVertex(nextX, y, nextZ)
      tessellator.addVertex(x, maxY, z)
      tessellator.addVertex(nextX, maxY, nextZ)
      tessellator.addVertex(x, y, z)
      tessellator.addVertex(x, maxY, z)
    }
    tessellator.draw()
  }

  private fun drawWorldLabel(
      minecraft: Minecraft,
      label: Label,
      cameraX: Double,
      cameraY: Double,
      cameraZ: Double,
      scale: Float,
  ) {
    val renderManager = RenderManager.instance
    val fontRenderer = minecraft.fontRenderer

    GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_COLOR_BUFFER_BIT)
    GL11.glPushMatrix()
    GL11.glTranslated(label.x - cameraX, label.y - cameraY, label.z - cameraZ)
    GL11.glNormal3f(0.0f, 1.0f, 0.0f)
    GL11.glRotatef(-renderManager.playerViewY, 0.0f, 1.0f, 0.0f)
    GL11.glRotatef(renderManager.playerViewX, 1.0f, 0.0f, 0.0f)
    GL11.glScalef(-scale, -scale, scale)
    GL11.glDisable(GL11.GL_LIGHTING)
    GL11.glDisable(GL11.GL_DEPTH_TEST)
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
    // The font goes through the lightmap; inside a block that is pitch black, so force full bright.
    val brightnessX = OpenGlHelper.lastBrightnessX
    val brightnessY = OpenGlHelper.lastBrightnessY
    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f)

    val lineHeight = fontRenderer.FONT_HEIGHT + 1
    val totalHeight = label.text.size * lineHeight
    label.text.forEachIndexed { index, line ->
      val color = if (index == 0) label.color.argbInt else SECONDARY_TEXT
      fontRenderer.drawStringWithShadow(
          line,
          -fontRenderer.getStringWidth(line) / 2,
          -totalHeight + index * lineHeight,
          color,
      )
    }
    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY)
    GL11.glPopMatrix()
    GL11.glPopAttrib()
  }

  private fun setColor(color: Color, alpha: Int) {
    GL11.glColor4f(color.red / 255.0f, color.green / 255.0f, color.blue / 255.0f, alpha / 255.0f)
  }

  private fun distance(
      x: Double,
      y: Double,
      z: Double,
      cx: Double,
      cy: Double,
      cz: Double,
  ): Double {
    val dx = x - cx
    val dy = y - cy
    val dz = z - cz
    return Math.sqrt(dx * dx + dy * dy + dz * dz)
  }

  private const val SECONDARY_TEXT = 0xFFE6E6E6.toInt()
}
