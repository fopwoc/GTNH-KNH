package io.github.fopwoc.mods.hotspot.server.network

import io.github.fopwoc.mods.hotspot.protocol.HotspotNetwork
import io.github.fopwoc.mods.hotspot.protocol.ProfileSnapshotPart
import io.github.fopwoc.mods.hotspot.protocol.ProfileStatus
import io.github.fopwoc.mods.hotspot.protocol.ProfileStatusMessage
import io.github.fopwoc.mods.hotspot.server.ProfilingService
import net.minecraft.entity.player.EntityPlayerMP

object ServerHotspotNetwork {
  fun initialize() {
    HotspotNetwork.installServerHandler { request, context ->
      ProfilingService.handle(context.serverHandler.playerEntity, request)
    }
  }

  fun sendStatus(
      player: EntityPlayerMP,
      requestId: Long,
      status: ProfileStatus,
      remainingTicks: Int,
  ) {
    HotspotNetwork.sendStatus(player, ProfileStatusMessage(requestId, status, remainingTicks))
  }

  fun sendPart(player: EntityPlayerMP, part: ProfileSnapshotPart) {
    HotspotNetwork.sendPart(player, part)
  }
}
