package io.github.fopwoc.mods.tabtps.server

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import io.github.fopwoc.mods.tabtps.server.network.ServerTpsNetwork
import io.github.fopwoc.mods.tabtps.server.sampling.MinecraftTpsSampler
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.entity.player.EntityPlayerMP

object ServerTpsService {
  private val pendingRequests = ConcurrentHashMap<EntityPlayerMP, PendingTpsRequest>()

  fun enqueue(player: EntityPlayerMP, request: PendingTpsRequest) {
    pendingRequests[player] = request
  }

  @SubscribeEvent
  fun onServerTick(event: TickEvent.ServerTickEvent) {
    if (event.phase != TickEvent.Phase.END || pendingRequests.isEmpty()) {
      return
    }

    for ((player, request) in pendingRequests.entries) {
      if (!pendingRequests.remove(player, request)) {
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
      ServerTpsNetwork.send(player, snapshot)
    }
  }
}
