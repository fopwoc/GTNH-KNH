package io.github.fopwoc.mods.tabtps.config.gui

import cpw.mods.fml.client.config.GuiConfig
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.tabtps.MOD_ID
import io.github.fopwoc.mods.tabtps.MOD_NAME
import io.github.fopwoc.mods.tabtps.config.TabTpsConfig
import net.minecraft.client.gui.GuiScreen

@SideOnly(Side.CLIENT)
class TabTpsConfigScreen(parent: GuiScreen) :
    GuiConfig(
        parent,
        TabTpsConfig.configElements(),
        MOD_ID,
        MOD_ID,
        false,
        false,
        "$MOD_NAME configuration",
    )
