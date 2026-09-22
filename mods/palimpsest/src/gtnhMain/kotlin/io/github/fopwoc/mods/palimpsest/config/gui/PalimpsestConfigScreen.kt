package io.github.fopwoc.mods.palimpsest.config.gui

import io.github.fopwoc.mods.palimpsest.ModMetadata.MOD_NAME
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.config.gui.ConfigScreen
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig
import net.minecraft.client.gui.GuiScreen

@SideOnly(Side.CLIENT)
class PalimpsestConfigScreen(parent: GuiScreen) :
    ConfigScreen(parent, PalimpsestConfig, "$MOD_NAME configuration")
