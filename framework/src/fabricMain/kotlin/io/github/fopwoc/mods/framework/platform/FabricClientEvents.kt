package io.github.fopwoc.mods.framework.platform

import io.github.fopwoc.mods.framework.event.ClientEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/** Client-only; installed only in the physical client. */
internal object FabricClientEvents {
    fun install() {
        ClientTickEvents.START_CLIENT_TICK.register { ClientEvents.tickStart.emit(Unit) }
        ClientTickEvents.END_CLIENT_TICK.register { client -> ClientEvents.afterTick(client.level != null) }
    }
}
