package io.github.fopwoc.mods.hotspot.server

import io.github.fopwoc.mods.hotspot.config.HotspotServerConfig
import io.github.fopwoc.mods.framework.platform.toEntityPlayer
import io.github.fopwoc.mods.framework.player.GamePlayer

/** Who may profile: the allow list from the server config, never op status. */
object HotspotAccess {
    fun isAllowed(player: GamePlayer): Boolean {
        if (HotspotServerConfig.allowEveryone) {
            return true
        }
        val server = player.toEntityPlayer()?.mcServer ?: return false
        if (server.isSinglePlayer && player.name.equals(server.serverOwner, true)) {
            return true
        }
        val keys = HotspotServerConfig.allowedPlayerKeys
        return player.name.lowercase() in keys || player.id.toString().lowercase() in keys
    }
}
