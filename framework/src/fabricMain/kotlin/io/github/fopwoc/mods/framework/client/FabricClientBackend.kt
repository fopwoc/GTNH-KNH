package io.github.fopwoc.mods.framework.client

import com.mojang.blaze3d.platform.InputConstants
import io.github.fopwoc.mods.framework.render.WorldOverlays
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.Component

class FabricClientBackend : ModernClientBackend() {
    /*? if >=26 {*/
    private val registeredCategories = mutableSetOf<KeyCategory>()

    override fun registerKeyMapping(mapping: KeyMapping, category: KeyCategory) {
        if (registeredCategories.add(category)) KeyMapping.Category.register(category.id())
        KeyMappingHelper.registerKeyMapping(mapping)
    }

    override fun boundKey(mapping: KeyMapping): InputConstants.Key =
        KeyMappingHelper.getBoundKeyOf(mapping)

    override fun installHud() {
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            io.github.fopwoc.mods.framework.minecraft.Identifier.fromNamespaceAndPath(
                "knhcore",
                "hud",
            )
        ) { graphics, _ ->
            renderHud(graphics)
        }
    }

    /*?} else {*/
    /*// Categories are plain translation keys on 1.21.1; Fabric registers them with the mapping.
    override fun registerKeyMapping(mapping: KeyMapping, category: KeyCategory) {
        KeyMappingHelper.registerKeyBinding(mapping)
    }

    override fun boundKey(mapping: KeyMapping): InputConstants.Key =
        KeyMappingHelper.getBoundKeyOf(mapping)

    override fun installHud() {
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register { graphics, _ ->
            renderHud(graphics)
        }
    }

    */
    /*?}*/
    override fun installWorldOverlays() {
        /*? if >=26 {*/
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents.END_EXTRACTION
            .register { context -> WorldOverlays.render(context.camera().position()) }
        /*?} else {*/
        /*net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.register { context ->
            WorldOverlays.render(context.camera())
        }
        */
        /*?}*/
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
