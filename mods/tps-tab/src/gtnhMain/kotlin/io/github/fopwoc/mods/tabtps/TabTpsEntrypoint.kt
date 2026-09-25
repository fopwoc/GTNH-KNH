package io.github.fopwoc.mods.tabtps

import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.platform.ModEntrypoint
import io.github.fopwoc.mods.framework.platform.toEntityPlayer
import io.github.fopwoc.mods.tabtps.config.TabTpsConfig
import io.github.fopwoc.mods.tabtps.monitor.TabTpsMonitor
import io.github.fopwoc.mods.tabtps.network.ClientTpsNetwork
import io.github.fopwoc.mods.tabtps.overlay.TabTpsOverlay
import io.github.fopwoc.mods.tabtps.protocol.TpsChannel
import io.github.fopwoc.mods.tabtps.server.ServerTpsService
import io.github.fopwoc.mods.tabtps.server.sampling.MinecraftTpsSampler

object TabTpsEntrypoint : ModEntrypoint {
    override val modId = ModMetadata.MOD_ID
    override val modName = ModMetadata.MOD_NAME
    override val modVersion = ModMetadata.MOD_VERSION

    override fun initialize() {
        TabTpsConfig.register()
        TpsChannel.requests.handle { request, player -> ServerTpsService.enqueue(player, request) }
        ServerTpsService.install { player, request ->
            val entity = player.toEntityPlayer() ?: return@install null
            MinecraftTpsSampler.sample(
                entity.mcServer,
                request.requestId,
                entity.dimension.toString(),
                request.dimensionIds,
            )
        }
    }

    override fun initializeClient() {
        ClientTpsNetwork.initialize()
        TabTpsMonitor.install()
        ClientBackend.current.registerHud(TabTpsOverlay)
    }
}
