package io.github.fopwoc.mods.tabtps.protocol

import cpw.mods.fml.common.network.NetworkRegistry
import cpw.mods.fml.common.network.simpleimpl.IMessage
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler
import cpw.mods.fml.common.network.simpleimpl.MessageContext
import cpw.mods.fml.relauncher.Side
import net.minecraft.entity.player.EntityPlayerMP

/**
 * Shared channel wiring. Both handlers run on the owning side's main thread in 1.7.10; invalid
 * (foreign-version or malformed) messages are dropped before reaching them.
 */
object TpsNetwork {
  private val channel = NetworkRegistry.INSTANCE.newSimpleChannel(TPS_CHANNEL_NAME)

  private var initialized = false
  private var requestHandler: ((TpsRequest, MessageContext) -> Unit)? = null
  private var snapshotHandler: ((TpsSnapshot) -> Unit)? = null

  fun installServerHandler(handler: (TpsRequest, MessageContext) -> Unit) {
    requestHandler = handler
    initialize()
  }

  fun installClientHandler(handler: (TpsSnapshot) -> Unit) {
    snapshotHandler = handler
    initialize()
  }

  fun requestSnapshot(request: TpsRequest) {
    channel.sendToServer(TpsRequestMessage(request.requestId, request.dimensionIds))
  }

  fun sendSnapshot(player: EntityPlayerMP, response: TpsSnapshotMessage) {
    channel.sendTo(response, player)
  }

  private fun initialize() {
    if (initialized) {
      return
    }

    channel.registerMessage(RequestMessageHandler, TpsRequestMessage::class.java, 0, Side.SERVER)
    channel.registerMessage(SnapshotMessageHandler, TpsSnapshotMessage::class.java, 1, Side.CLIENT)
    initialized = true
  }

  private object RequestMessageHandler : IMessageHandler<TpsRequestMessage, IMessage> {
    override fun onMessage(message: TpsRequestMessage, context: MessageContext): IMessage? {
      message.request?.let { requestHandler?.invoke(it, context) }
      return null
    }
  }

  private object SnapshotMessageHandler : IMessageHandler<TpsSnapshotMessage, IMessage> {
    override fun onMessage(message: TpsSnapshotMessage, context: MessageContext): IMessage? {
      message.snapshot?.let { snapshotHandler?.invoke(it) }
      return null
    }
  }
}
