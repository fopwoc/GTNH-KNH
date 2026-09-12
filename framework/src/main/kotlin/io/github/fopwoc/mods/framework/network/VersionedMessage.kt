package io.github.fopwoc.mods.framework.network

import cpw.mods.fml.common.network.simpleimpl.IMessage
import io.netty.buffer.ByteBuf

/**
 * An [IMessage] whose payload is a plain value and whose decoding never throws.
 *
 * In 1.7.10, an exception escaping `fromBytes` makes FML drop the connection, so a foreign protocol
 * version, a truncated buffer or an out-of-range count must all end up as "no payload" and be
 * ignored by the handler. Subclasses write [encode]/[decode] as straight code against
 * [MessageReader]; the version header and the catch live here. [ModChannel] only invokes handlers
 * for messages with a payload.
 *
 * ```kotlin
 * class PingMessage() : VersionedMessage<Ping>(PROTOCOL_VERSION) {
 *   constructor(ping: Ping) : this() { payload = ping }
 *   override fun encode(buffer: ByteBuf, payload: Ping) { buffer.writeLong(payload.nonce) }
 *   override fun decode(reader: MessageReader): Ping = Ping(reader.long())
 * }
 * ```
 */
abstract class VersionedMessage<P : Any>(private val protocolVersion: Int) : IMessage {
  var payload: P? = null
    protected set

  protected abstract fun encode(buffer: ByteBuf, payload: P)

  /**
   * Read the payload; throw [MalformedMessageException] (or let [MessageReader] do it) to reject.
   */
  protected abstract fun decode(reader: MessageReader): P

  final override fun fromBytes(buffer: ByteBuf) {
    payload = null
    val reader = MessageReader(buffer)
    payload =
        try {
          if (reader.int() != protocolVersion) return
          decode(reader)
        } catch (_: MalformedMessageException) {
          null
        }
  }

  final override fun toBytes(buffer: ByteBuf) {
    val payload = checkNotNull(payload) { "${javaClass.simpleName} has no payload to encode" }
    buffer.writeInt(protocolVersion)
    encode(buffer, payload)
  }
}
