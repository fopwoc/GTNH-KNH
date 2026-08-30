package io.github.fopwoc.mods.testgui.client.gui

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.client.Minecraft

@SideOnly(Side.CLIENT)
object TestGuiScreenController {
  private var requestedScreen: RequestedScreen? = null

  fun requestOpen() {
    requestedScreen = RequestedScreen.MainDemo
  }

  @SubscribeEvent
  fun onClientTick(event: TickEvent.ClientTickEvent) {
    val nextScreen = requestedScreen
    if (event.phase != TickEvent.Phase.END || nextScreen == null) {
      return
    }

    val minecraft = Minecraft.getMinecraft()
    if (minecraft.thePlayer == null || minecraft.theWorld == null) {
      requestedScreen = null
      return
    }

    val alreadyOpen =
        when (nextScreen) {
          RequestedScreen.MainDemo -> minecraft.currentScreen is TestGuiScreen
        }
    if (alreadyOpen) {
      requestedScreen = null
      return
    }

    requestedScreen = null
    minecraft.displayGuiScreen(
        when (nextScreen) {
          RequestedScreen.MainDemo -> TestGuiScreen()
        }
    )
  }

  private enum class RequestedScreen {
    MainDemo
  }
}
