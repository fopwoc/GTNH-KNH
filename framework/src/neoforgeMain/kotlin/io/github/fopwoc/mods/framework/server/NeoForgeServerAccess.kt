package io.github.fopwoc.mods.framework.server

import java.util.UUID
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.server.ServerLifecycleHooks

class NeoForgeServerAccess : ServerAccess {
    override fun player(id: UUID): ServerPlayer? = ServerLifecycleHooks.getCurrentServer()?.playerList?.getPlayer(id)

    override fun worldTickTimes(level: ServerLevel): TickSamples? {
        val server = level.server
        val samples: LongArray? = server.getTickTime(level.dimension())
        if (samples == null) return null
        return TickSamples(samples, Math.floorMod(server.tickCount, samples.size))
    }
}
