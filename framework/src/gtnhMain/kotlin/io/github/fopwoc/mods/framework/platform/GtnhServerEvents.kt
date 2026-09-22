package io.github.fopwoc.mods.framework.platform

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.PlayerEvent
import cpw.mods.fml.common.gameevent.TickEvent
import io.github.fopwoc.mods.framework.event.ServerEvents
import io.github.fopwoc.mods.framework.player.GamePlayer
import net.minecraft.entity.player.EntityPlayer

object GtnhServerEvents {
    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        when (event.phase) {
            TickEvent.Phase.START -> ServerEvents.tickStart.emit(Unit)
            TickEvent.Phase.END -> ServerEvents.tickEnd.emit(Unit)
            null -> Unit
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) = ServerEvents.playerJoined.emit(event.player.toGamePlayer())

    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) = ServerEvents.playerLeft.emit(event.player.toGamePlayer())

    /** Server lifecycle reaches only `@Mod` classes on 1.7.10; FrameworkBootstrap forwards it. */
    fun serverStarted() = ServerEvents.started.emit(Unit)

    fun serverStopping() = ServerEvents.stopping.emit(Unit)
}

fun EntityPlayer.toGamePlayer(): GamePlayer = GamePlayer(uniqueID, commandSenderName)
