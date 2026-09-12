package io.github.fopwoc.mods.framework.network

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionedMessageTest {
  private data class Ping(val nonce: Long, val tags: List<String>, val kind: Kind)

  private enum class Kind {
    A,
    B,
  }

  private class PingMessage() : VersionedMessage<Ping>(VERSION) {
    constructor(ping: Ping) : this() {
      payload = ping
    }

    override fun encode(buffer: ByteBuf, payload: Ping) {
      buffer.writeLong(payload.nonce)
      buffer.writeByte(payload.tags.size)
      payload.tags.forEach { buffer.writeUtf8(it, 16) }
      buffer.writeEnum(payload.kind)
    }

    override fun decode(reader: MessageReader): Ping =
        Ping(
            nonce = reader.long(),
            tags = reader.list(4, { unsignedByte() }) { utf8(16) },
            kind = reader.enum<Kind>(),
        )
  }

  @Test
  fun roundTrips() {
    val ping = Ping(42, listOf("a", "bb"), Kind.B)
    val buffer = Unpooled.buffer()
    PingMessage(ping).toBytes(buffer)

    assertEquals(ping, PingMessage().also { it.fromBytes(buffer) }.payload)
    assertEquals(0, buffer.readableBytes())
  }

  @Test
  fun foreignVersionTruncationAndOversizedListsDecodeAsNoPayload() {
    val foreign = Unpooled.buffer().writeInt(VERSION + 1).writeLong(1)
    assertNull(PingMessage().also { it.fromBytes(foreign) }.payload)

    val truncated = Unpooled.buffer().writeInt(VERSION).writeInt(1)
    assertNull(PingMessage().also { it.fromBytes(truncated) }.payload)

    val tooMany = Unpooled.buffer().writeInt(VERSION).writeLong(1).writeByte(5)
    assertNull(PingMessage().also { it.fromBytes(tooMany) }.payload)

    val badEnum = Unpooled.buffer().writeInt(VERSION).writeLong(1).writeByte(0).writeByte(7)
    assertNull(PingMessage().also { it.fromBytes(badEnum) }.payload)

    val longString = Unpooled.buffer().writeInt(VERSION).writeLong(1).writeByte(1)
    longString.writeUtf8("x".repeat(64), 64)
    assertNull(PingMessage().also { it.fromBytes(longString) }.payload)
  }

  private companion object {
    const val VERSION = 3
  }
}
