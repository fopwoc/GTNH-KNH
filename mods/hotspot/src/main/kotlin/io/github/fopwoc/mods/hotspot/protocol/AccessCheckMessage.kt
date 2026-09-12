package io.github.fopwoc.mods.hotspot.protocol

import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.VersionedMessage
import io.netty.buffer.ByteBuf

class AccessCheckMessage() : VersionedMessage<AccessCheck>(HOTSPOT_PROTOCOL_VERSION) {
  constructor(check: AccessCheck) : this() {
    payload = check
  }

  override fun encode(buffer: ByteBuf, payload: AccessCheck) {
    buffer.writeLong(payload.nonce)
  }

  override fun decode(reader: MessageReader): AccessCheck = AccessCheck(reader.long())
}
