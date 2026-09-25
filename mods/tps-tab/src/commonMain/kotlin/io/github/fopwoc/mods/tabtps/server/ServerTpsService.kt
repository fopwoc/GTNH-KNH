package io.github.fopwoc.mods.tabtps.server

import io.github.fopwoc.mods.framework.event.ServerEvents
import io.github.fopwoc.mods.framework.player.GamePlayer
import io.github.fopwoc.mods.tabtps.protocol.TpsChannel
import io.github.fopwoc.mods.tabtps.protocol.TpsRequest
import io.github.fopwoc.mods.tabtps.protocol.TpsSnapshot

/**
 * Answers TPS requests once per server tick. Requests arrive on the server thread; keeping only the
 * latest request per player coalesces bursts, so a client can never get more than one snapshot per
 * tick no matter how fast it asks.
 */
object ServerTpsService {
    private val pendingRequests = LinkedHashMap<GamePlayer, TpsRequest>()
    private lateinit var sample: (GamePlayer, TpsRequest) -> TpsSnapshot?

    fun enqueue(player: GamePlayer, request: TpsRequest) {
        pendingRequests[player] = request
    }

    fun install(sampler: (GamePlayer, TpsRequest) -> TpsSnapshot?) {
        sample = sampler
        ServerEvents.tickEnd.subscribe { answerPending() }
    }

    private fun answerPending() {
        if (pendingRequests.isEmpty()) {
            return
        }

        val requests = pendingRequests.toList()
        pendingRequests.clear()
        for ((player, request) in requests) {
            sample(player, request)?.let { TpsChannel.snapshots.send(player, it) }
        }
    }
}
