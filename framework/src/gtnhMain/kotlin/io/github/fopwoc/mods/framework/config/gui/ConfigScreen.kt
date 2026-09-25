package io.github.fopwoc.mods.framework.config.gui

import cpw.mods.fml.client.config.GuiConfig
import cpw.mods.fml.client.config.IConfigElement
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.config.ForgeConfigFiles
import io.github.fopwoc.mods.framework.config.IntConfigValue
import io.github.fopwoc.mods.framework.config.ModConfig
import net.minecraft.client.gui.GuiScreen
import net.minecraftforge.common.config.ConfigElement

/** Vanilla-styled settings screen for a [ModConfig]; saving fires `ConfigChangedEvent`. */
@SideOnly(Side.CLIENT)
open class ConfigScreen(parent: GuiScreen, config: ModConfig, title: String) :
    GuiConfig(parent, config.elements(), config.modId, config.modId, false, false, title)

@SideOnly(Side.CLIENT)
private fun ModConfig.elements(): List<IConfigElement<*>> {
    val binding = ForgeConfigFiles.binding(this)
    val hinted =
        values
            .filterIsInstance<IntConfigValue>()
            .filter { it.hint != null }
            .associateBy { it.languageKey }
    ConfigHints.register(hinted)
    for (property in binding.bindAll()) {
        if (property.languageKey in hinted) {
            property.setConfigEntryClass(HintedIntegerEntry::class.java)
        }
    }
    return ConfigElement<Any>(binding.configuration.getCategory(binding.category)).childElements
}
