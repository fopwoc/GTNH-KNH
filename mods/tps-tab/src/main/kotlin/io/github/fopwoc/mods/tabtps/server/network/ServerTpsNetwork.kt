package io.github.fopwoc.mods.tabtps.server.network

import io.github.fopwoc.mods.tabtps.protocol.TpsNetwork
import io.github.fopwoc.mods.tabtps.protocol.TpsSnapshot
import io.github.fopwoc.mods.tabtps.protocol.TpsSnapshotMessage
import io.github.fopwoc.mods.tabtps.server.ServerTpsService
import net.minecraft.entity.player.EntityPlayerMP

object ServerTpsNetwork {
  fun initialize() {
    TpsNetwork.installServerHandler { request, context ->
      ServerTpsService.enqueue(player = context.serverHandler.playerEntity, request = request)
    }
  }

  fun send(player: EntityPlayerMP, snapshot: TpsSnapshot) {
    TpsNetwork.sendSnapshot(player, TpsSnapshotMessage(snapshot))
  }
}
