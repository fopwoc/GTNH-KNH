package io.github.fopwoc.mods.hotspot.client.overlay

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.hotspot.client.profile.ProfileSessionStatus
import io.github.fopwoc.mods.hotspot.client.profile.ProfileStore
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderGameOverlayEvent

/** One line above the hotbar while a run is in flight, so the menu can be closed meanwhile. */
@SideOnly(Side.CLIENT)
object HotspotStatusHud {
  @SubscribeEvent
  fun onRender(event: RenderGameOverlayEvent.Post) {
    if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) {
      return
    }
    val minecraft = Minecraft.getMinecraft()
    if (minecraft.currentScreen != null || minecraft.gameSettings.hideGUI) {
      return
    }
    val text =
        when (val status = ProfileStore.status) {
          ProfileSessionStatus.Waiting -> "Hotspot · waiting for the server…"
          is ProfileSessionStatus.Profiling ->
              "Hotspot · profiling… ${(status.remainingTicks + 19) / 20} s"
          ProfileSessionStatus.Receiving -> "Hotspot · receiving snapshot…"
          is ProfileSessionStatus.Failed -> "Hotspot · ${status.reason}"
          ProfileSessionStatus.Idle -> return
        }
    val font = minecraft.fontRenderer
    val x = (event.resolution.scaledWidth - font.getStringWidth(text)) / 2
    val y = event.resolution.scaledHeight - HOTBAR_OFFSET
    font.drawStringWithShadow(text, x, y, TEXT_COLOR)
  }

  private const val HOTBAR_OFFSET = 52
  private const val TEXT_COLOR = 0xFFE6E6E6.toInt()
}
