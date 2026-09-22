package io.github.fopwoc.mods.framework.platform

import io.github.fopwoc.mods.framework.player.GamePlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.server.MinecraftServer

/** The online player behind [GamePlayer] on the running logical server, if any. */
fun GamePlayer.toEntityPlayer(): EntityPlayerMP? {
    val players = MinecraftServer.getServer()?.configurationManager?.playerEntityList ?: return null
    return players.filterIsInstance<EntityPlayerMP>().firstOrNull { it.uniqueID == id }
}
