package io.github.fopwoc.mods.framework.network

import cpw.mods.fml.common.network.NetworkRegistry
import cpw.mods.fml.common.network.simpleimpl.IMessage
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler
import cpw.mods.fml.common.network.simpleimpl.MessageContext
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper
import cpw.mods.fml.relauncher.Side
import net.minecraft.entity.player.EntityPlayerMP

/**
 * A `SimpleNetworkWrapper` channel for [VersionedMessage]s. Handlers only ever see messages that
 * decoded successfully, and they run on the receiving side's main thread (FML queues custom
 * payloads through the vanilla packet queue in 1.7.10).
 *
 * Declare every message once, as a property of the channel object, so both sides register them in
 * the same order (discriminators are sequential). Attach handlers from the proxy that owns the
 * receiving side; declaring is side-neutral, so a clientbound message can be sent by a dedicated
 * server that never installs its handler.
 *
 * ```kotlin
 * object PingChannel : ModChannel("mymod") {
 *   val pings = serverbound(PingMessage::class.java)
 *   val pongs = clientbound(PongMessage::class.java)
 * }
 * // CommonProxy.init: PingChannel.pings.handle { ping, player -> PongService.answer(player, ping) }
 * // ClientProxy.init: PingChannel.pongs.handle { pong -> PongStore.record(pong) }
 * ```
 */
open class ModChannel(val name: String) {
  private val wrapper: SimpleNetworkWrapper = NetworkRegistry.INSTANCE.newSimpleChannel(name)
  private var nextDiscriminator = 0

  /** Client → server messages of one type; [Serverbound.handle] receives the sending player. */
  fun <M : VersionedMessage<P>, P : Any> serverbound(type: Class<M>): Serverbound<M, P> =
      Serverbound<M, P>().also { register(type, Side.SERVER, it) }

  /** Server → client messages of one type. */
  fun <M : VersionedMessage<P>, P : Any> clientbound(type: Class<M>): Clientbound<M, P> =
      Clientbound<M, P>().also { register(type, Side.CLIENT, it) }

  private fun <M : VersionedMessage<P>, P : Any> register(
      type: Class<M>,
      side: Side,
      handler: IMessageHandler<M, IMessage>,
  ) {
    wrapper.registerMessage(handler, type, nextDiscriminator++, side)
  }

  abstract inner class Inbound<M : VersionedMessage<P>, P : Any, C> : IMessageHandler<M, IMessage> {
    private var handler: ((P, C) -> Unit)? = null

    protected fun install(handler: (P, C) -> Unit) {
      this.handler = handler
    }

    protected abstract fun context(context: MessageContext): C

    final override fun onMessage(message: M, context: MessageContext): IMessage? {
      val payload = message.payload ?: return null
      handler?.invoke(payload, context(context))
      return null
    }
  }

  inner class Serverbound<M : VersionedMessage<P>, P : Any> : Inbound<M, P, EntityPlayerMP>() {
    fun handle(handler: (payload: P, sender: EntityPlayerMP) -> Unit) = install(handler)

    fun send(message: M) = wrapper.sendToServer(message)

    override fun context(context: MessageContext): EntityPlayerMP =
        context.serverHandler.playerEntity
  }

  inner class Clientbound<M : VersionedMessage<P>, P : Any> : Inbound<M, P, Unit>() {
    fun handle(handler: (payload: P) -> Unit) = install { payload, _ -> handler(payload) }

    fun send(player: EntityPlayerMP, message: M) = wrapper.sendTo(message, player)

    override fun context(context: MessageContext) = Unit
  }
}
