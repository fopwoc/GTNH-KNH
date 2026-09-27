package io.github.fopwoc.mods.framework.config.gui

import cpw.mods.fml.client.config.GuiConfig
import cpw.mods.fml.client.config.GuiConfigEntries
import cpw.mods.fml.client.config.IConfigElement
import java.util.Locale
import net.minecraft.client.resources.I18n
import net.minecraft.util.StatCollector

/** Shows translated enum choices while keeping their stable config-file values. */
@Suppress("unused") // Forge instantiates it by class.
class LocalizedEnumEntry(
    owningScreen: GuiConfig,
    owningEntryList: GuiConfigEntries,
    configElement: IConfigElement<*>,
) :
    GuiConfigEntries.SelectValueEntry(
        owningScreen,
        owningEntryList,
        configElement as IConfigElement<String>,
        configElement.validValues.associateWith { configElement.label(it.toString()) },
    ) {
    override fun updateValueButtonText() {
        btnValue.displayString = configElement.label(currentValue.toString())
    }
}

private fun IConfigElement<*>.label(value: String): String {
    val key = "$languageKey.${value.lowercase(Locale.ROOT)}"
    return if (StatCollector.canTranslate(key)) I18n.format(key) else value
}
