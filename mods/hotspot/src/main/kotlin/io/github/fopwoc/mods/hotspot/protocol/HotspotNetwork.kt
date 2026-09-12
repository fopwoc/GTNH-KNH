package io.github.fopwoc.mods.hotspot.protocol

import cpw.mods.fml.common.network.NetworkRegistry
import cpw.mods.fml.common.network.simpleimpl.IMessage
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler
import cpw.mods.fml.common.network.simpleimpl.MessageContext
import cpw.mods.fml.relauncher.Side
import net.minecraft.entity.player.EntityPlayerMP

/**
 * Shared channel wiring. Handlers run on the owning side's main thread in 1.7.10; malformed or
 * foreign-version messages are dropped before reaching them.
 */
object HotspotNetwork {
  private val channel = NetworkRegistry.INSTANCE.newSimpleChannel(HOTSPOT_CHANNEL_NAME)

  private var initialized = false
  private var requestHandler: ((ProfileRequest, MessageContext) -> Unit)? = null
  private var statusHandler: ((ProfileStatusMessage) -> Unit)? = null
  private var partHandler: ((ProfileSnapshotPart) -> Unit)? = null

  fun installServerHandler(handler: (ProfileRequest, MessageContext) -> Unit) {
    requestHandler = handler
    initialize()
  }

  fun installClientHandlers(
      onStatus: (ProfileStatusMessage) -> Unit,
      onPart: (ProfileSnapshotPart) -> Unit,
  ) {
    statusHandler = onStatus
    partHandler = onPart
    initialize()
  }

  fun sendRequest(request: ProfileRequest) {
    channel.sendToServer(ProfileRequestMessage(request))
  }

  fun sendStatus(player: EntityPlayerMP, message: ProfileStatusMessage) {
    channel.sendTo(message, player)
  }

  fun sendPart(player: EntityPlayerMP, part: ProfileSnapshotPart) {
    channel.sendTo(ProfileSnapshotPartMessage(part), player)
  }

  private fun initialize() {
    if (initialized) {
      return
    }
    channel.registerMessage(RequestHandler, ProfileRequestMessage::class.java, 0, Side.SERVER)
    channel.registerMessage(StatusHandler, ProfileStatusMessage::class.java, 1, Side.CLIENT)
    channel.registerMessage(PartHandler, ProfileSnapshotPartMessage::class.java, 2, Side.CLIENT)
    initialized = true
  }

  private object RequestHandler : IMessageHandler<ProfileRequestMessage, IMessage> {
    override fun onMessage(message: ProfileRequestMessage, context: MessageContext): IMessage? {
      message.request?.let { requestHandler?.invoke(it, context) }
      return null
    }
  }

  private object StatusHandler : IMessageHandler<ProfileStatusMessage, IMessage> {
    override fun onMessage(message: ProfileStatusMessage, context: MessageContext): IMessage? {
      if (message.status != null) statusHandler?.invoke(message)
      return null
    }
  }

  private object PartHandler : IMessageHandler<ProfileSnapshotPartMessage, IMessage> {
    override fun onMessage(
        message: ProfileSnapshotPartMessage,
        context: MessageContext,
    ): IMessage? {
      message.part?.let { partHandler?.invoke(it) }
      return null
    }
  }
}
