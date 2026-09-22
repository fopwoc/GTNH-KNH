package io.github.fopwoc.mods.framework.network

/** Writes and reads one message payload; [decode] throws [MalformedMessageException] to reject. */
interface MessageCodec<P : Any> {
    fun encode(writer: MessageWriter, payload: P)

    fun decode(reader: MessageReader): P
}
