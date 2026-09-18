package io.github.fopwoc.mods.framework.network

import cpw.mods.fml.common.network.ByteBufUtils
import io.netty.buffer.ByteBuf

/**
 * Bounds-checked reads over a [ByteBuf]. Every read throws [MalformedMessageException] instead of
 * Netty's `IndexOutOfBoundsException` when the payload runs out, so a decoder can be written as a
 * straight sequence of reads and [VersionedMessage] turns the failure into "invalid, ignored".
 */
class MessageReader(private val buffer: ByteBuf) {
    private companion object {
        const val MAX_UTF8_BYTES_PER_CHAR: Int = 3
    }

    fun boolean(): Boolean = unsignedByte() != 0

    fun byte(): Byte = need(1).readByte()

    fun unsignedByte(): Int = need(1).readUnsignedByte().toInt()

    fun short(): Int = need(2).readShort().toInt()

    fun unsignedShort(): Int = need(2).readUnsignedShort()

    fun int(): Int = need(Int.SIZE_BYTES).readInt()

    fun long(): Long = need(Long.SIZE_BYTES).readLong()

    fun float(): Float = need(Float.SIZE_BYTES).readFloat()

    fun double(): Double = need(Double.SIZE_BYTES).readDouble()

    /**
     * Varint length + UTF-8, as [ByteBufUtils.writeUTF8String] writes; capped at [maxLength] chars.
     */
    fun utf8(maxLength: Int): String {
        need(1)
        val length = ByteBufUtils.readVarInt(buffer, 2)
        if (length < 0 || length > maxLength * MAX_UTF8_BYTES_PER_CHAR) {
            throw MalformedMessageException("String of $length bytes exceeds $maxLength chars")
        }
        val bytes = ByteArray(length)
        need(length).readBytes(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * Reads `count` (from [readCount]) elements, refusing counts above [max] before reading any.
     */
    inline fun <T> list(
        max: Int,
        readCount: MessageReader.() -> Int,
        element: MessageReader.() -> T,
    ): List<T> {
        val count = readCount()
        if (count < 0 || count > max) {
            throw MalformedMessageException("List of $count elements exceeds $max")
        }
        return List(count) { element() }
    }

    /** Reads an enum ordinal stored as one unsigned byte. */
    inline fun <reified E : Enum<E>> enum(): E {
        val ordinal = unsignedByte()
        return enumValues<E>().getOrNull(ordinal)
            ?: throw MalformedMessageException("Unknown ${E::class.simpleName} ordinal $ordinal")
    }

    fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw MalformedMessageException(message())
    }

    val isExhausted: Boolean
        get() = !buffer.isReadable

    private fun need(bytes: Int): ByteBuf {
        if (buffer.readableBytes() < bytes) {
            throw MalformedMessageException(
                "Payload truncated: need $bytes, have ${buffer.readableBytes()}"
            )
        }
        return buffer
    }
}
