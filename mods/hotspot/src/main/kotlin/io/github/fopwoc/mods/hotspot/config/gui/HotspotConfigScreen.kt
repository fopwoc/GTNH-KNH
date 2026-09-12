package io.github.fopwoc.mods.hotspot.config.gui

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.config.gui.ConfigScreen
import io.github.fopwoc.mods.hotspot.MOD_NAME
import io.github.fopwoc.mods.hotspot.config.HotspotConfig
import net.minecraft.client.gui.GuiScreen

@SideOnly(Side.CLIENT)
class HotspotConfigScreen(parent: GuiScreen) :
    ConfigScreen(parent, HotspotConfig, "$MOD_NAME configuration")
