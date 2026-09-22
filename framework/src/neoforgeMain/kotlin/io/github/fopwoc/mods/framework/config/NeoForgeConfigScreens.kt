package io.github.fopwoc.mods.framework.config

import java.util.concurrent.ConcurrentHashMap
import net.neoforged.fml.ModContainer
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory

/** Client-only: NeoForge's settings screen behind the mod list's config button. */
internal object NeoForgeConfigScreens {
    private val registered = ConcurrentHashMap.newKeySet<String>()

    fun register(container: ModContainer) {
        if (registered.add(container.modId)) {
            container.registerExtensionPoint(IConfigScreenFactory::class.java, IConfigScreenFactory { mod, parent -> ConfigurationScreen(mod, parent) })
        }
    }
}
