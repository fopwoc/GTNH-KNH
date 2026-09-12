package io.github.fopwoc.mods.hotspot.client.overlay

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.format.TimeFormat
import io.github.fopwoc.mods.framework.render.WorldOverlay
import io.github.fopwoc.mods.hotspot.client.profile.ProfileStore
import io.github.fopwoc.mods.hotspot.config.HotspotConfig
import kotlin.math.sqrt
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderWorldLastEvent

/**
 * Draws what the menu picked: highlighted chunks as glass columns, highlighted tile entities as
 * glass boxes, both with ms labels. Colour is the item's share of the heaviest highlighted item, so
 * the worst one is always red.
 */
@SideOnly(Side.CLIENT)
object HotspotOverlayRenderer {
  @SubscribeEvent
  fun onRenderWorld(event: RenderWorldLastEvent) {
    val world = Minecraft.getMinecraft().theWorld ?: return
    val dimensionId = world.provider.dimensionId
    val chunks = ProfileStore.highlightedChunks(dimensionId)
    val tileEntities = ProfileStore.highlightedTileEntities(dimensionId)
    if (chunks.isEmpty() && tileEntities.isEmpty()) {
      return
    }
    val labelRange = HotspotConfig.labelDistance.toDouble()
    val showLabels = HotspotConfig.showLabels
    val columnHeight = HotspotConfig.chunkColumnHeight.toDouble()

    WorldOverlay.render(event.partialTicks) {
      val chunkMaxMs = chunks.maxOfOrNull { ProfileStore.chunk(it)?.totalMs ?: 0.0 } ?: 0.0
      for (ref in chunks) {
        val profile = ProfileStore.chunk(ref) ?: continue
        val heat = HeatScale.color(if (chunkMaxMs > 0.0) profile.totalMs / chunkMaxMs else 0.0)
        val minX = ref.chunkX * 16.0
        val minZ = ref.chunkZ * 16.0
        glassBox(minX, 0.0, minZ, minX + 16.0, columnHeight, minZ + 16.0, heat)
        if (showLabels) {
          label(
              minX + 8.0,
              camera.y + 2.0,
              minZ + 8.0,
              listOf(TimeFormat.millisAdaptive(profile.totalMs), "(${ref.chunkX}, ${ref.chunkZ})"),
              heat,
              scale = 0.04f,
          )
        }
      }

      val tileEntityMaxMs =
          tileEntities.maxOfOrNull { ProfileStore.tileEntity(it)?.ms ?: 0.0 } ?: 0.0
      for (ref in tileEntities) {
        val profile = ProfileStore.tileEntity(ref) ?: continue
        val heat = HeatScale.color(if (tileEntityMaxMs > 0.0) profile.ms / tileEntityMaxMs else 0.0)
        glassBox(
            ref.x.toDouble(),
            ref.y.toDouble(),
            ref.z.toDouble(),
            ref.x + 1.0,
            ref.y + 1.0,
            ref.z + 1.0,
            heat,
        )
        val dx = ref.x + 0.5 - camera.x
        val dy = ref.y + 0.5 - camera.y
        val dz = ref.z + 0.5 - camera.z
        if (showLabels && sqrt(dx * dx + dy * dy + dz * dz) <= labelRange) {
          val lines = mutableListOf(TimeFormat.millisAdaptive(profile.ms), profile.name)
          if (HotspotConfig.showTechnicalNames && profile.className.isNotEmpty()) {
            lines += profile.className
          }
          label(ref.x + 0.5, ref.y + 1.35, ref.z + 0.5, lines, heat, scale = 0.02f)
        }
      }
    }
  }
}
