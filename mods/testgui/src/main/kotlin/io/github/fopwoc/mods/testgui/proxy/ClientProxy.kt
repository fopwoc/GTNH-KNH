package io.github.fopwoc.mods.testgui.proxy

import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.testgui.client.command.TestGuiCommand
import io.github.fopwoc.mods.testgui.client.hud.TestGuiHudOverlay
import net.minecraftforge.common.MinecraftForge

@Suppress("unused")
class ClientProxy : ModProxy() {
  override fun init() {
    TestGuiCommand.register()
    MinecraftForge.EVENT_BUS.register(TestGuiHudOverlay)
  }
}
