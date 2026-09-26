package io.github.fopwoc.mods.framework.client

import com.mojang.blaze3d.platform.InputConstants
import io.github.fopwoc.mods.framework.render.WorldOverlays
import io.github.fopwoc.mods.framework.ui.compose.hud.HudPlacement
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

    // 26.x draws the debug screen after the whole HUD, so both placements can go last.
    override fun installHud(placement: HudPlacement) {
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            io.github.fopwoc.mods.framework.minecraft.Identifier.fromNamespaceAndPath(
                "knhcore",
                placement.elementPath,
            )
        ) { graphics, _ ->
            renderHud(graphics, placement)
        }
    }

    /*?} else {*/
    /*// Categories are plain translation keys on 1.21.1; Fabric registers them with the mapping.
    override fun registerKeyMapping(mapping: KeyMapping, category: KeyCategory) {
        KeyMappingHelper.registerKeyBinding(mapping)
    }

    override fun boundKey(mapping: KeyMapping): InputConstants.Key =
        KeyMappingHelper.getBoundKeyOf(mapping)

    // The callback runs after the debug screen; while it shows, DebugScreenOverlayMixin draws the
    // layers meant to sit under it instead.
    override fun installHud(placement: HudPlacement) {
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register { graphics, _ ->
            val debugShown =
                net.minecraft.client.Minecraft.getInstance().gui.debugOverlay.showDebugScreen()
            if (placement == HudPlacement.TOP || !debugShown) renderHud(graphics, placement)
        }
    }

    /** Draws the layers under the debug screen; its mixin calls this just before it draws. */
    fun renderBelowDebug(graphics: net.minecraft.client.gui.GuiGraphics) =
        renderHud(graphics, HudPlacement.BELOW_DEBUG)

    */
    /*?}*/
    override fun installWorldOverlays() {
        /*? if >=26.2 {*/
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents.END_EXTRACTION
            .register { context -> WorldOverlays.render(context.camera().position()) }
        /*?} elif >=26 {*/
        /*net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_EXTRACTION
           .register { context -> WorldOverlays.render(context.camera().position()) }
        */
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
