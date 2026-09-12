package io.github.fopwoc.mods.hotspot.client.gui

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.Minecraft

/** Opens the screen on the next client tick, outside whatever input handler asked for it. */
@SideOnly(Side.CLIENT)
object HotspotScreenController {
  private var openRequested = false

  fun requestOpen() {
    openRequested = true
  }

  @SubscribeEvent
  fun onClientTick(event: TickEvent.ClientTickEvent) {
    if (event.phase != TickEvent.Phase.END || !openRequested) {
      return
    }
    openRequested = false
    val minecraft = Minecraft.getMinecraft()
    if (minecraft.thePlayer != null && minecraft.theWorld != null) {
      minecraft.displayGuiScreen(HotspotScreen())
    }
  }
}
