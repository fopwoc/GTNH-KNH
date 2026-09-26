/*? if >=26 {*/
// Not ported to 1.21.1 yet: the whole file exists only from 26.x.
package io.github.fopwoc.mods.framework.client

import com.mojang.blaze3d.platform.InputConstants
import io.github.fopwoc.mods.framework.ModMetadata
import net.minecraft.client.KeyMapping
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.neoforged.fml.ModList
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.common.NeoForge

class NeoForgeClientBackend : ModernClientBackend() {
    private val pendingMappings = mutableListOf<Pair<KeyMapping, KeyMapping.Category>>()

    override fun registerKeyMapping(mapping: KeyMapping, category: KeyMapping.Category) {
        if (pendingMappings.isEmpty()) {
            // NeoForge takes key mappings in one mod-bus event after every mod was constructed.
            framework().eventBus?.addListener(RegisterKeyMappingsEvent::class.java) { event ->
                pendingMappings.map { it.second }.distinct().forEach(event::registerCategory)
                pendingMappings.forEach { (it, _) -> event.register(it) }
            }
        }
        pendingMappings += mapping to category
    }

    override fun boundKey(mapping: KeyMapping): InputConstants.Key = mapping.key

    private fun framework() = ModList.get().getModContainerById(ModMetadata.MOD_ID).orElseThrow()

    override fun installHud() {
        // Layers are registered once, on the framework's mod bus, after every mod was constructed.
        framework().eventBus?.addListener(RegisterGuiLayersEvent::class.java) { event ->
            event.registerAboveAll(Identifier.fromNamespaceAndPath(ModMetadata.MOD_ID, "hud")) {
                graphics,
                _ ->
                renderHud(graphics)
            }
        }
    }

    override fun installCommands() {
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent::class.java) { event ->
            commands.forEach { command ->
                event.dispatcher.register(
                    brigadier<CommandSourceStack>(command) { source, text ->
                        source.sendSystemMessage(Component.literal(text))
                    }
                )
            }
        }
    }
}
/*?}*/
