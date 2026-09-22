package io.github.fopwoc.mods.tabtps.proxy

import io.github.fopwoc.mods.framework.log.logger
import cpw.mods.fml.common.FMLCommonHandler
import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.tabtps.protocol.TpsChannel
import io.github.fopwoc.mods.tabtps.server.ServerTpsService

open class CommonProxy : ModProxy() {
    private val logger = logger<CommonProxy>()

    override fun init() {
        TpsChannel.requests.handle { request, player -> ServerTpsService.enqueue(player, request) }
        FMLCommonHandler.instance().bus().register(ServerTpsService)
        logger.info("Registered shared TPS protocol and server sampling service")
    }
}
