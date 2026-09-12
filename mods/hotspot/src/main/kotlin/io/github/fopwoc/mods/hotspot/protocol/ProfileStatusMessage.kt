package io.github.fopwoc.mods.hotspot.protocol

import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.VersionedMessage
import io.github.fopwoc.mods.framework.network.writeEnum
import io.netty.buffer.ByteBuf

class ProfileStatusMessage() : VersionedMessage<ProfileStatusUpdate>(HOTSPOT_PROTOCOL_VERSION) {
  constructor(update: ProfileStatusUpdate) : this() {
    payload = update
  }

  override fun encode(buffer: ByteBuf, payload: ProfileStatusUpdate) {
    buffer.writeLong(payload.requestId)
    buffer.writeEnum(payload.status)
    buffer.writeInt(payload.remainingTicks)
  }

  override fun decode(reader: MessageReader): ProfileStatusUpdate =
      ProfileStatusUpdate(
          requestId = reader.long(),
          status = reader.enum<ProfileStatus>(),
          remainingTicks = reader.int(),
      )
}
