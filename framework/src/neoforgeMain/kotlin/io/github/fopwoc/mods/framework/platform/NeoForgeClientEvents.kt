package io.github.fopwoc.mods.framework.platform

import io.github.fopwoc.mods.framework.event.ClientEvents
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge

/** Client-only; installed only in the physical client. */
internal object NeoForgeClientEvents {
    fun install() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre::class.java) { ClientEvents.tickStart.emit(Unit) }
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post::class.java) {
            ClientEvents.afterTick(Minecraft.getInstance().level != null)
        }
    }
}
