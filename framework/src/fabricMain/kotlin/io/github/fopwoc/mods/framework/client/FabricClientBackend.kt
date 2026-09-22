package io.github.fopwoc.mods.framework.client

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class FabricClientBackend : ModernClientBackend() {
    override fun installHud() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("knhcore", "hud")) { graphics, _ -> renderHud(graphics) }
    }

    override fun installCommands() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            commands.forEach { command ->
                dispatcher.register(brigadier<FabricClientCommandSource>(command) { source, text -> source.sendFeedback(Component.literal(text)) })
            }
        }
    }
}
