package io.github.fopwoc.mods.hotspot.protocol

import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.VersionedMessage
import io.netty.buffer.ByteBuf

class AccessReplyMessage() : VersionedMessage<AccessReply>(HOTSPOT_PROTOCOL_VERSION) {
  constructor(reply: AccessReply) : this() {
    payload = reply
  }

  override fun encode(buffer: ByteBuf, payload: AccessReply) {
    buffer.writeLong(payload.nonce)
    buffer.writeBoolean(payload.allowed)
    buffer.writeBoolean(payload.profilerAvailable)
    buffer.writeInt(payload.maxDurationTicks)
  }

  override fun decode(reader: MessageReader): AccessReply =
      AccessReply(
          nonce = reader.long(),
          allowed = reader.boolean(),
          profilerAvailable = reader.boolean(),
          maxDurationTicks = reader.int(),
      )
}
