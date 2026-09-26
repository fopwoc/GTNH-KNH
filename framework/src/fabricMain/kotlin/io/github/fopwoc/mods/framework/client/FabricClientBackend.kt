/*? if >=26 {*/
// Not ported to 1.21.1 yet: the whole file exists only from 26.x.
package io.github.fopwoc.mods.framework.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class FabricClientBackend : ModernClientBackend() {
    private val registeredCategories = mutableSetOf<KeyMapping.Category>()

    override fun registerKeyMapping(mapping: KeyMapping, category: KeyMapping.Category) {
        if (registeredCategories.add(category)) KeyMapping.Category.register(category.id())
        KeyMappingHelper.registerKeyMapping(mapping)
    }

    override fun boundKey(mapping: KeyMapping): InputConstants.Key =
        KeyMappingHelper.getBoundKeyOf(mapping)

    override fun installHud() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("knhcore", "hud")) { graphics, _
            ->
            renderHud(graphics)
        }
    }

    override fun installCommands() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            commands.forEach { command ->
                dispatcher.register(
                    brigadier<FabricClientCommandSource>(command) { source, text ->
                        source.sendFeedback(Component.literal(text))
                    }
                )
            }
        }
    }
}
/*?}*/
