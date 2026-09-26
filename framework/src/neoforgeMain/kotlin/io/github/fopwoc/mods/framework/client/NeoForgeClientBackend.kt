package io.github.fopwoc.mods.framework.client

import com.mojang.blaze3d.platform.InputConstants
import io.github.fopwoc.mods.framework.ModMetadata
import io.github.fopwoc.mods.framework.minecraft.Identifier
import io.github.fopwoc.mods.framework.render.WorldOverlays
import io.github.fopwoc.mods.framework.ui.compose.hud.HudPlacement
import net.minecraft.client.KeyMapping
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.neoforged.fml.ModList
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.common.NeoForge

class NeoForgeClientBackend : ModernClientBackend() {
    private val pendingMappings = mutableListOf<Pair<KeyMapping, KeyCategory>>()

    override fun registerKeyMapping(mapping: KeyMapping, category: KeyCategory) {
        if (pendingMappings.isEmpty()) {
            // NeoForge takes key mappings in one mod-bus event after every mod was constructed.
            framework().eventBus?.addListener(RegisterKeyMappingsEvent::class.java) { event ->
                /*? if >=26 {*/
                pendingMappings.map { it.second }.distinct().forEach(event::registerCategory)
                /*?}*/
                pendingMappings.forEach { (it, _) -> event.register(it) }
            }
        }
        pendingMappings += mapping to category
    }

    override fun boundKey(mapping: KeyMapping): InputConstants.Key = mapping.key

    private fun framework() = ModList.get().getModContainerById(ModMetadata.MOD_ID).orElseThrow()

    override fun installHud(placement: HudPlacement) {
        // Layers are registered once, on the framework's mod bus, after every mod was constructed.
        framework().eventBus?.addListener(RegisterGuiLayersEvent::class.java) { event ->
            val id = Identifier.fromNamespaceAndPath(ModMetadata.MOD_ID, placement.elementPath)
            when (placement) {
                HudPlacement.TOP ->
                    event.registerAboveAll(id) { graphics, _ -> renderHud(graphics, placement) }
                HudPlacement.BELOW_DEBUG -> {
                    /*? if >=26 {*/
                    // 26.x draws the debug screen after the whole HUD, so any layer is under it.
                    event.registerAboveAll(id) { graphics, _ -> renderHud(graphics, placement) }
                    /*?} else {*/
                    /*event.registerBelow(
                        net.neoforged.neoforge.client.gui.VanillaGuiLayers.DEBUG_OVERLAY,
                        id,
                    ) { graphics, _ ->
                        renderHud(graphics, placement)
                    }
                    */
                    /*?}*/
                }
            }
        }
    }

    override fun installWorldOverlays() {
        /*? if >=26 {*/
        NeoForge.EVENT_BUS.addListener(
            net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent::class.java
        ) { event ->
            WorldOverlays.render(event.camera.position())
        }
        /*?} else {*/
        /*NeoForge.EVENT_BUS.addListener(
            net.neoforged.neoforge.client.event.RenderLevelStageEvent::class.java
        ) { event ->
            if (
                event.stage ==
                    net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL
            )
                WorldOverlays.render(event.camera)
        }
        */
        /*?}*/
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
