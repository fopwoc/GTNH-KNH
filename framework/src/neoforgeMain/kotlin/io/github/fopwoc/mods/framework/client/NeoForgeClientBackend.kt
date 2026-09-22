package io.github.fopwoc.mods.framework.client

import io.github.fopwoc.mods.framework.ModMetadata
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.neoforged.fml.ModList
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.common.NeoForge

class NeoForgeClientBackend : ModernClientBackend() {
    override fun installHud() {
        // Layers are registered once, on the framework's mod bus, after every mod was constructed.
        val framework = ModList.get().getModContainerById(ModMetadata.MOD_ID).orElseThrow()
        framework.eventBus?.addListener(RegisterGuiLayersEvent::class.java) { event ->
            event.registerAboveAll(Identifier.fromNamespaceAndPath(ModMetadata.MOD_ID, "hud")) { graphics, _ -> renderHud(graphics) }
        }
    }

    override fun installCommands() {
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent::class.java) { event ->
            commands.forEach { command ->
                event.dispatcher.register(brigadier<CommandSourceStack>(command) { source, text -> source.sendSystemMessage(Component.literal(text)) })
            }
        }
    }
}
