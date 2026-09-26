package io.github.fopwoc.mods.framework.config

import java.util.concurrent.ConcurrentHashMap
import net.neoforged.neoforge.client.gui.ConfigurationScreen

/** Client-only: the ported NeoForge settings screen, reachable from Mod Menu. */
internal object FabricConfigScreens {
    private val registered = ConcurrentHashMap.newKeySet<String>()

    fun register(modId: String) {
        if (registered.add(modId)) {
            ConfigScreenFactoryRegistry.INSTANCE.register(modId) { id, parent ->
                ConfigurationScreen(id, parent)
            }
        }
    }
}
