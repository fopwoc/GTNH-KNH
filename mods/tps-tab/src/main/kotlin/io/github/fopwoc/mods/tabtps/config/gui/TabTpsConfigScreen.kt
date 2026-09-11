package io.github.fopwoc.mods.tabtps.config.gui

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.config.gui.ConfigScreen
import io.github.fopwoc.mods.tabtps.MOD_NAME
import io.github.fopwoc.mods.tabtps.config.TabTpsConfig
import net.minecraft.client.gui.GuiScreen

@SideOnly(Side.CLIENT)
class TabTpsConfigScreen(parent: GuiScreen) :
    ConfigScreen(parent, TabTpsConfig, "$MOD_NAME configuration")
