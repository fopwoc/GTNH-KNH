package io.github.fopwoc.mods.framework.client

import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen

/**
 * Opens a screen on the next client tick, outside whatever input or command handler asked for it,
 * and only when the player is in a world. Registers itself on the FML bus on first use.
 */
@SideOnly(Side.CLIENT)
object ScreenOpener {
  private var pending: (() -> GuiScreen)? = null
  private var registered = false

  fun open(factory: () -> GuiScreen) {
    if (!registered) {
      registered = true
      FMLCommonHandler.instance().bus().register(this)
    }
    pending = factory
  }

  @SubscribeEvent
  fun onClientTick(event: TickEvent.ClientTickEvent) {
    if (event.phase != TickEvent.Phase.END) {
      return
    }
    val factory = pending ?: return
    pending = null
    val minecraft = Minecraft.getMinecraft()
    if (minecraft.thePlayer != null && minecraft.theWorld != null) {
      minecraft.displayGuiScreen(factory())
    }
  }
}
