package io.github.fopwoc.mods.hotspot.server

import io.github.fopwoc.mods.hotspot.config.HotspotServerConfig
import net.minecraft.entity.player.EntityPlayerMP

/** Who may profile: the allow list from the server config, never op status. */
object HotspotAccess {
  fun isAllowed(player: EntityPlayerMP): Boolean {
    if (HotspotServerConfig.allowEveryone) {
      return true
    }
    val server = player.mcServer
    if (server.isSinglePlayer && player.commandSenderName.equals(server.serverOwner, true)) {
      return true
    }
    val keys = HotspotServerConfig.allowedPlayerKeys
    return player.commandSenderName.lowercase() in keys ||
        player.uniqueID.toString().lowercase() in keys
  }
}
