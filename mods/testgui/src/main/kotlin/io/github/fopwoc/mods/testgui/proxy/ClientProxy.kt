package io.github.fopwoc.mods.testgui.proxy

import cpw.mods.fml.common.FMLCommonHandler
import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.testgui.TestGuiMod
import io.github.fopwoc.mods.testgui.client.command.OpenTestGuiCommand
import io.github.fopwoc.mods.testgui.client.gui.TestGuiScreenController
import net.minecraftforge.client.ClientCommandHandler

@Suppress("unused")
class ClientProxy : ModProxy() {
    override fun init() {
        FMLCommonHandler.instance().bus().register(TestGuiScreenController)
        ClientCommandHandler.instance.registerCommand(OpenTestGuiCommand)
        TestGuiMod.logger.info("Registered test GUI demo command")
    }
}

