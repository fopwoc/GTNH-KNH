package io.github.fopwoc.mods.framework.platform

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.event.ClientEvents
import net.minecraft.client.Minecraft

@SideOnly(Side.CLIENT)
object GtnhClientEvents {
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        when (event.phase) {
            TickEvent.Phase.START -> ClientEvents.tickStart.emit(Unit)
            TickEvent.Phase.END -> ClientEvents.afterTick(Minecraft.getMinecraft().theWorld != null)
            null -> Unit
        }
    }
}
