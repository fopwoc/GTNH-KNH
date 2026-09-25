package io.github.fopwoc.mods.framework.network

import io.github.fopwoc.mods.framework.player.GamePlayer
import java.util.ServiceLoader

/**
 * Moves [ModChannel] frames over the loader's custom payloads. A peer without the channel (the mod
 * missing on the other side) must never break the connection: sends to it are dropped, and the
 * client can ask [isAvailableOnServer] to stay quiet.
 */
interface NetworkBackend {
    /** Called once per channel from its constructor, during mod initialization. */
    fun register(channel: ModChannel)

    fun sendToServer(channel: ModChannel, frame: ByteArray)

    fun sendToPlayer(channel: ModChannel, player: GamePlayer, frame: ByteArray)

    /** Client side: whether the connected server has the channel. */
    fun isAvailableOnServer(channel: ModChannel): Boolean

    companion object {
        val current: NetworkBackend by lazy {
            ServiceLoader.load(NetworkBackend::class.java, NetworkBackend::class.java.classLoader)
                .firstOrNull() ?: OfflineNetworkBackend
        }
    }
}

/** Unit tests and headless tools: channels exist, nothing is connected. */
private object OfflineNetworkBackend : NetworkBackend {
    override fun register(channel: ModChannel) = Unit

    override fun sendToServer(channel: ModChannel, frame: ByteArray) = Unit

    override fun sendToPlayer(channel: ModChannel, player: GamePlayer, frame: ByteArray) = Unit

    override fun isAvailableOnServer(channel: ModChannel): Boolean = false
}
