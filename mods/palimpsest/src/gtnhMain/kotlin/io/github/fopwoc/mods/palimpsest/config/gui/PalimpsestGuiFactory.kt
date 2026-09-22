package io.github.fopwoc.mods.palimpsest.config.gui

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.config.gui.ConfigGuiFactory
import net.minecraft.client.gui.GuiScreen

@SideOnly(Side.CLIENT)
class PalimpsestGuiFactory : ConfigGuiFactory() {
    override fun screenClass(): Class<out GuiScreen> = PalimpsestConfigScreen::class.java
}
