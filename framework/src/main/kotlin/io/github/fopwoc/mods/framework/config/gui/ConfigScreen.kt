package io.github.fopwoc.mods.framework.config.gui

import cpw.mods.fml.client.config.GuiConfig
import cpw.mods.fml.client.config.IConfigElement
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.config.ForgeConfig
import net.minecraft.client.gui.GuiScreen
import net.minecraftforge.common.config.ConfigElement

/** Vanilla-styled settings screen for a [ForgeConfig]; saving fires `ConfigChangedEvent`. */
@SideOnly(Side.CLIENT)
open class ConfigScreen(parent: GuiScreen, config: ForgeConfig, title: String) :
    GuiConfig(parent, config.elements(), config.modId, config.modId, false, false, title)

@SideOnly(Side.CLIENT)
private fun ForgeConfig.elements(): List<IConfigElement<*>> {
  bindAll()
  return ConfigElement<Any>(boundConfiguration().getCategory(categoryName())).childElements
}
