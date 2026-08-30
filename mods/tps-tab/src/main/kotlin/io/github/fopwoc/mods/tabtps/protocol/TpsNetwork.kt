package io.github.fopwoc.mods.tabtps.protocol

import cpw.mods.fml.common.network.NetworkRegistry
import cpw.mods.fml.common.network.simpleimpl.IMessage
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler
import cpw.mods.fml.common.network.simpleimpl.MessageContext
import cpw.mods.fml.relauncher.Side
import net.minecraft.entity.player.EntityPlayerMP

object TpsNetwork {
  private val channel = NetworkRegistry.INSTANCE.newSimpleChannel(TPS_CHANNEL_NAME)

  @Volatile private var initialized = false
  @Volatile private var requestHandler: ((TpsRequestMessage, MessageContext) -> Unit)? = null
  @Volatile private var snapshotHandler: ((TpsSnapshotMessage) -> Unit)? = null

  fun installServerHandler(handler: (TpsRequestMessage, MessageContext) -> Unit) {
    requestHandler = handler
    initialize()
  }

  fun installClientHandler(handler: (TpsSnapshotMessage) -> Unit) {
    snapshotHandler = handler
    initialize()
  }

  fun requestSnapshot(request: TpsRequestMessage) {
    channel.sendToServer(request)
  }

  fun sendSnapshot(player: EntityPlayerMP, response: TpsSnapshotMessage) {
    channel.sendTo(response, player)
  }

  @Synchronized
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
      requestHandler?.invoke(message, context)
      return null
    }
  }

  private object SnapshotMessageHandler : IMessageHandler<TpsSnapshotMessage, IMessage> {
    override fun onMessage(message: TpsSnapshotMessage, context: MessageContext): IMessage? {
      snapshotHandler?.invoke(message)
      return null
    }
  }
}
