package io.github.fopwoc.mods.framework.server

import java.util.UUID
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

class FabricServerAccess : ServerAccess {
    private var server: MinecraftServer? = null

    init {
        ServerLifecycleEvents.SERVER_STARTING.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register { server = null }
    }

    override fun player(id: UUID): ServerPlayer? = server?.playerList?.getPlayer(id)

    override fun worldTickTimes(level: ServerLevel): TickSamples? {
        val samples = level as LevelTickSamples
        return samples.lastTickIndex.takeIf { it >= 0 }?.let { TickSamples(samples.tickTimesNanos, it) }
    }
}
