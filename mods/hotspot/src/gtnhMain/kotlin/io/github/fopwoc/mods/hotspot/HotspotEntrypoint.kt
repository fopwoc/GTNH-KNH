package io.github.fopwoc.mods.hotspot

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.framework.platform.ModEntrypoint
import io.github.fopwoc.mods.hotspot.client.HotspotKeyBindings
import io.github.fopwoc.mods.hotspot.client.command.HotspotCommand
import io.github.fopwoc.mods.hotspot.client.overlay.HotspotOverlayRenderer
import io.github.fopwoc.mods.hotspot.client.overlay.HotspotStatusHud
import io.github.fopwoc.mods.hotspot.client.profile.ProfileStore
import io.github.fopwoc.mods.hotspot.config.HotspotConfig
import io.github.fopwoc.mods.hotspot.config.HotspotServerConfig
import io.github.fopwoc.mods.hotspot.protocol.HotspotChannel
import io.github.fopwoc.mods.hotspot.server.ProfilingService
import io.github.fopwoc.mods.hotspot.server.profiler.OpisAvailability
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.common.MinecraftForge

object HotspotEntrypoint : ModEntrypoint {
    private val logger = logger<HotspotEntrypoint>()

    override val modId = ModMetadata.MOD_ID
    override val modName = ModMetadata.MOD_NAME
    override val modVersion = ModMetadata.MOD_VERSION

    override fun initialize() {
        HotspotServerConfig.register()
        HotspotChannel.requests.handle { request, player -> ProfilingService.handle(player, request) }
        HotspotChannel.accessChecks.handle { check, player -> ProfilingService.answerAccessCheck(player, check) }
        ProfilingService.install()
        if (OpisAvailability.isPresent) {
            logger.info("Opis profiler available; profiling requests will be served")
        } else {
            logger.info("Opis not installed; profiling requests will be refused")
        }
    }

    override fun initializeClient() {
        HotspotConfig.register()
        HotspotChannel.statuses.handle(ProfileStore::onStatus)
        HotspotChannel.parts.handle(ProfileStore::onSnapshotPart)
        HotspotChannel.accessReplies.handle(ProfileStore::onAccessReply)
        ProfileStore.install()
        MinecraftForge.EVENT_BUS.register(HotspotOverlayRenderer)
        MinecraftForge.EVENT_BUS.register(HotspotStatusHud)
        HotspotCommand.register()
        HotspotKeyBindings.register()
    }
}
