package io.github.fopwoc.mods.tabtps

import io.github.fopwoc.mods.framework.platform.ModEntrypoint
import io.github.fopwoc.mods.tabtps.config.TabTpsConfig
import io.github.fopwoc.mods.tabtps.monitor.TabTpsMonitor
import io.github.fopwoc.mods.tabtps.network.ClientTpsNetwork
import io.github.fopwoc.mods.tabtps.overlay.TabTpsOverlay
import io.github.fopwoc.mods.tabtps.protocol.TpsChannel
import io.github.fopwoc.mods.tabtps.server.ServerTpsService
import net.minecraftforge.common.MinecraftForge

object TabTpsEntrypoint : ModEntrypoint {
    override val modId = ModMetadata.MOD_ID
    override val modName = ModMetadata.MOD_NAME
    override val modVersion = ModMetadata.MOD_VERSION

    override fun initialize() {
        TabTpsConfig.register()
        TpsChannel.requests.handle { request, player -> ServerTpsService.enqueue(player, request) }
        ServerTpsService.install()
    }

    override fun initializeClient() {
        ClientTpsNetwork.initialize()
        TabTpsMonitor.install()
        MinecraftForge.EVENT_BUS.register(TabTpsOverlay)
    }
}
