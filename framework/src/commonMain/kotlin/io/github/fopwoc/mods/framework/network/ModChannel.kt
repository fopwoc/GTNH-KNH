package io.github.fopwoc.mods.framework.network

import io.github.fopwoc.mods.framework.log.Logger
import io.github.fopwoc.mods.framework.player.GamePlayer

/**
 * A mod's network channel: typed messages over one custom payload per loader, identified as
 * `namespace:path`. Every frame is `[message index][protocol version][payload]`; frames of another
 * protocol version, unknown messages and malformed payloads are dropped, never a disconnect.
 * Handlers run on the receiving side's game thread.
 *
 * Declare every message once, as a property of the channel object, so both sides number them in
 * the same order. Create the channel during mod initialization; attach handlers where the
 * receiving side is set up. Declaring is side-neutral, so a dedicated server can send a clientbound
 * message without ever installing its handler.
 *
 * ```kotlin
 * object PingChannel : ModChannel(MOD_ID, protocolVersion = 1) {
 *   val pings = serverbound(PingCodec)
 *   val pongs = clientbound(PongCodec)
 * }
 * // initialize():       PingChannel.pings.handle { ping, player -> PongService.answer(player, ping) }
 * // initializeClient(): PingChannel.pongs.handle { pong -> PongStore.record(pong) }
 * ```
 */
open class ModChannel internal constructor(
    val namespace: String,
    val path: String,
    val protocolVersion: Int,
    private val backend: NetworkBackend,
) {
    constructor(namespace: String, path: String = "main", protocolVersion: Int) :
        this(namespace, path, protocolVersion, NetworkBackend.current)

    private val logger = Logger.of(ModChannel::class)
    private val messages = mutableListOf<Message<*>>()

    val id: String get() = "$namespace:$path"

    /** Client side: whether the connected server has this channel, i.e. the mod is installed there. */
    val isAvailableOnServer: Boolean get() = backend.isAvailableOnServer(this)

    init {
        backend.register(this)
    }

    /** Client → server messages; handlers receive the sending player. */
    fun <P : Any> serverbound(codec: MessageCodec<P>): Serverbound<P> = Serverbound(messages.size, codec).also(messages::add)

    /** Server → client messages. */
    fun <P : Any> clientbound(codec: MessageCodec<P>): Clientbound<P> = Clientbound(messages.size, codec).also(messages::add)

    /** Backends pass every received frame; [sender] is the sending player on the server, null on the client. */
    fun receive(frame: ByteArray, sender: GamePlayer?) {
        try {
            val reader = MessageReader(frame)
            val message = messages.getOrNull(reader.unsignedByte()) ?: return
            if (reader.int() != protocolVersion) return
            message.dispatch(reader, sender)
        } catch (exception: MalformedMessageException) {
            logger.debug("Dropped malformed {} frame: {}", id, exception.message)
        }
    }

    sealed class Message<P : Any>(private val index: Int, protected val codec: MessageCodec<P>) {
        internal abstract fun dispatch(reader: MessageReader, sender: GamePlayer?)

        protected fun frame(protocolVersion: Int, payload: P): ByteArray =
            MessageWriter().byte(index).int(protocolVersion).also { codec.encode(it, payload) }.toByteArray()
    }

    inner class Serverbound<P : Any> internal constructor(index: Int, codec: MessageCodec<P>) : Message<P>(index, codec) {
        private var handler: ((P, GamePlayer) -> Unit)? = null

        fun handle(handler: (payload: P, sender: GamePlayer) -> Unit) {
            this.handler = handler
        }

        /** Dropped when the server lacks the channel. */
        fun send(payload: P) = backend.sendToServer(this@ModChannel, frame(protocolVersion, payload))

        override fun dispatch(reader: MessageReader, sender: GamePlayer?) {
            val player = sender ?: return
            val handler = handler ?: return
            handler(codec.decode(reader), player)
        }
    }

    inner class Clientbound<P : Any> internal constructor(index: Int, codec: MessageCodec<P>) : Message<P>(index, codec) {
        private var handler: ((P) -> Unit)? = null

        fun handle(handler: (payload: P) -> Unit) {
            this.handler = handler
        }

        /** Dropped when the player's client lacks the channel. */
        fun send(player: GamePlayer, payload: P) =
            backend.sendToPlayer(this@ModChannel, player, frame(protocolVersion, payload))

        override fun dispatch(reader: MessageReader, sender: GamePlayer?) {
            if (sender != null) return
            val handler = handler ?: return
            handler(codec.decode(reader))
        }
    }
}
