package io.github.fopwoc.mods.framework.network

import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.network.FMLEventChannel
import cpw.mods.fml.common.network.NetworkRegistry
import cpw.mods.fml.common.network.internal.FMLProxyPacket
import io.github.fopwoc.mods.framework.platform.toEntityPlayer
import io.github.fopwoc.mods.framework.player.GamePlayer
import io.netty.buffer.Unpooled
import java.util.concurrent.ConcurrentHashMap

/**
 * One FML event-driven channel per [ModChannel], named after it (20 characters at most). The frame
 * layout equals `SimpleNetworkWrapper`'s discriminator + message, so peers built on either agree.
 */
class GtnhNetworkBackend : NetworkBackend {
    private val channels = ConcurrentHashMap<ModChannel, FMLEventChannel>()

    override fun register(channel: ModChannel) {
        val name = channel.legacyName
        val fml = NetworkRegistry.INSTANCE.newEventDrivenChannel(name)
        fml.register(GtnhChannelListener(channel, name))
        channels[channel] = fml
        if (FMLCommonHandler.instance().side.isClient) GtnhServerChannels.install()
    }

    override fun sendToServer(channel: ModChannel, frame: ByteArray) {
        if (!isAvailableOnServer(channel)) return
        fml(channel).sendToServer(packet(channel, frame))
    }

    override fun sendToPlayer(channel: ModChannel, player: GamePlayer, frame: ByteArray) {
        // A vanilla or mod-less client ignores payloads of channels it does not know.
        val entity = player.toEntityPlayer() ?: return
        fml(channel).sendTo(packet(channel, frame), entity)
    }

    override fun isAvailableOnServer(channel: ModChannel): Boolean = GtnhServerChannels.isAvailable(channel.legacyName)

    private fun fml(channel: ModChannel): FMLEventChannel =
        checkNotNull(channels[channel]) { "Channel ${channel.id} is not registered" }

    private fun packet(channel: ModChannel, frame: ByteArray) = FMLProxyPacket(Unpooled.wrappedBuffer(frame), channel.legacyName)
}

/** 1.7.10 channel names are one string of at most 20 characters; a `main` path keeps just the namespace. */
internal val ModChannel.legacyName: String
    get() = (if (path == "main") namespace else "$namespace:$path").also {
        check(it.length <= 20) { "Channel $id is longer than the 20 characters 1.7.10 allows" }
    }
