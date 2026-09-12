package io.github.fopwoc.mods.tabtps.server

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import io.github.fopwoc.mods.tabtps.protocol.TpsChannel
import io.github.fopwoc.mods.tabtps.protocol.TpsRequest
import io.github.fopwoc.mods.tabtps.protocol.TpsSnapshotMessage
import io.github.fopwoc.mods.tabtps.server.sampling.MinecraftTpsSampler
import net.minecraft.entity.player.EntityPlayerMP

/**
 * Answers TPS requests once per server tick. Requests arrive on the server thread; keeping only the
 * latest request per player coalesces bursts, so a client can never get more than one snapshot per
 * tick no matter how fast it asks.
 */
object ServerTpsService {
  private val pendingRequests = LinkedHashMap<EntityPlayerMP, TpsRequest>()

  fun enqueue(player: EntityPlayerMP, request: TpsRequest) {
    pendingRequests[player] = request
  }

  @SubscribeEvent
  fun onServerTick(event: TickEvent.ServerTickEvent) {
    if (event.phase != TickEvent.Phase.END || pendingRequests.isEmpty()) {
      return
    }

    val requests = pendingRequests.toList()
    pendingRequests.clear()
    for ((player, request) in requests) {
      if (player.playerNetServerHandler.netManager?.isChannelOpen != true) {
        continue
      }

      val server = player.mcServer
      val snapshot =
          MinecraftTpsSampler.sample(
              server = server,
              requestId = request.requestId,
              currentDimensionId = player.dimension,
              dimensionIds = request.dimensionIds,
          )
      TpsChannel.snapshots.send(player, TpsSnapshotMessage(snapshot))
    }
  }
}
