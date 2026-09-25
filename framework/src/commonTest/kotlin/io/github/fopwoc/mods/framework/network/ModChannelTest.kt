package io.github.fopwoc.mods.framework.network

import io.github.fopwoc.mods.framework.player.GamePlayer
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModChannelTest {
    private data class Ping(val nonce: Long, val tags: List<String>, val kind: Kind)

    private enum class Kind {
        A,
        B,
    }

    private object PingCodec : MessageCodec<Ping> {
        override fun encode(writer: MessageWriter, payload: Ping) {
            writer.long(payload.nonce)
            writer.byte(payload.tags.size)
            payload.tags.forEach { writer.utf8(it, 16) }
            writer.enum(payload.kind)
        }

        override fun decode(reader: MessageReader): Ping =
            Ping(
                nonce = reader.long(),
                tags = reader.list(4, { unsignedByte() }) { utf8(16) },
                kind = reader.enum<Kind>(),
            )
    }

    private class RecordingBackend : NetworkBackend {
        val toServer = mutableListOf<ByteArray>()
        val toPlayers = mutableListOf<Pair<GamePlayer, ByteArray>>()

        override fun register(channel: ModChannel) = Unit

        override fun sendToServer(channel: ModChannel, frame: ByteArray) {
            toServer += frame
        }

        override fun sendToPlayer(channel: ModChannel, player: GamePlayer, frame: ByteArray) {
            toPlayers += player to frame
        }

        override fun isAvailableOnServer(channel: ModChannel) = true
    }

    private class TestChannel(backend: NetworkBackend) :
        ModChannel("test", "main", VERSION, backend) {
        val pings = serverbound(PingCodec)
        val pongs = clientbound(PingCodec)
    }

    private val player = GamePlayer(UUID.randomUUID(), "Steve")
    private val backend = RecordingBackend()
    private val channel = TestChannel(backend)
    private val received = mutableListOf<Pair<Ping, GamePlayer?>>()

    init {
        channel.pings.handle { ping, sender -> received += ping to sender }
        channel.pongs.handle { pong -> received += pong to null }
    }

    @Test
    fun serverboundAndClientboundRoundTrip() {
        val ping = Ping(42, listOf("a", "bb"), Kind.B)
        channel.pings.send(ping)
        channel.receive(backend.toServer.single(), player)
        channel.pongs.send(player, ping)
        channel.receive(backend.toPlayers.single().second, null)

        assertEquals(listOf(ping to player, ping to null), received)
    }

    @Test
    fun frameMatchesSimpleNetworkWrapperLayout() {
        channel.pongs.send(player, Ping(1, emptyList(), Kind.A))
        val frame = MessageReader(backend.toPlayers.single().second)

        assertEquals(1, frame.unsignedByte())
        assertEquals(VERSION, frame.int())
        assertEquals(1L, frame.long())
    }

    @Test
    fun foreignVersionsMalformedPayloadsAndWrongDirectionsAreDropped() {
        fun frame(index: Int, version: Int, body: MessageWriter.() -> Unit) =
            MessageWriter().byte(index).int(version).apply(body).toByteArray()

        channel.receive(frame(0, VERSION + 1) { long(1).byte(0).byte(0) }, player)
        channel.receive(frame(0, VERSION) { int(1) }, player)
        channel.receive(frame(0, VERSION) { long(1).byte(5) }, player)
        channel.receive(frame(0, VERSION) { long(1).byte(0).byte(7) }, player)
        channel.receive(frame(0, VERSION) { long(1).byte(1).utf8("x".repeat(64), 64) }, player)
        channel.receive(frame(9, VERSION) { long(1).byte(0).byte(0) }, player)
        channel.receive(frame(1, VERSION) { long(1).byte(0).byte(0) }, player)
        channel.receive(frame(0, VERSION) { long(1).byte(0).byte(0) }, null)
        channel.receive(ByteArray(0), player)

        assertTrue(received.isEmpty(), "$received")
    }

    private companion object {
        const val VERSION = 3
    }
}
