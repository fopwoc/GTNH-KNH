package io.github.fopwoc.mods.framework.config.gui

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.config.IntConfigValue

/**
 * Forge instantiates settings entries reflectively by class, so a hinted entry finds the setting it
 * renders here, by language key, which is unique per mod and setting.
 */
@SideOnly(Side.CLIENT)
internal object ConfigHints {
    private val values = HashMap<String, IntConfigValue>()

    fun register(values: Map<String, IntConfigValue>) {
        this.values.putAll(values)
    }

    fun hintFor(languageKey: String, value: Int): String? = values[languageKey]?.hint?.invoke(value)
}
