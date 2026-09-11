package io.github.fopwoc.mods.testgui.proxy

import cpw.mods.fml.common.FMLCommonHandler
import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.testgui.TestGuiMod
import io.github.fopwoc.mods.testgui.client.command.OpenTestGuiCommand
import io.github.fopwoc.mods.testgui.client.gui.TestGuiScreenController
import io.github.fopwoc.mods.testgui.client.hud.TestGuiHudOverlay
import net.minecraftforge.client.ClientCommandHandler
import net.minecraftforge.common.MinecraftForge

@Suppress("unused")
class ClientProxy : ModProxy() {
  override fun init() {
    FMLCommonHandler.instance().bus().register(TestGuiScreenController)
    ClientCommandHandler.instance.registerCommand(OpenTestGuiCommand)
    MinecraftForge.EVENT_BUS.register(TestGuiHudOverlay)
    TestGuiMod.logger.info("Registered test GUI demo commands")
  }
}
