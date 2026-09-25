package io.github.fopwoc.mods.framework.network

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

/**
 * Bounds-checked reads over a received payload. Every read throws [MalformedMessageException] when
 * the payload runs out or exceeds a declared bound, so a codec is a straight sequence of reads and
 * [ModChannel] turns any failure into "invalid, ignored".
 */
class MessageReader(bytes: ByteArray) {
    private val buffer = ByteBuffer.wrap(bytes)

    fun boolean(): Boolean = unsignedByte() != 0

    fun byte(): Byte = read { get() }

    fun unsignedByte(): Int = byte().toInt() and 0xFF

    fun short(): Int = read { short }.toInt()

    fun unsignedShort(): Int = short() and 0xFFFF

    fun int(): Int = read { int }

    fun long(): Long = read { long }

    fun float(): Float = read { float }

    fun double(): Double = read { double }

    /** Varint byte length + UTF-8, as [MessageWriter.utf8] writes; capped at [maxLength] chars. */
    fun utf8(maxLength: Int): String {
        val length = varInt(MAX_STRING_VARINT_BYTES)
        if (length < 0 || length > maxLength * MAX_UTF8_BYTES_PER_CHAR) {
            throw MalformedMessageException("String of $length bytes exceeds $maxLength chars")
        }
        val bytes = ByteArray(length)
        read { get(bytes) }
        return String(bytes, Charsets.UTF_8)
    }

    fun varInt(maxBytes: Int = MAX_VARINT_BYTES): Int {
        var value = 0
        repeat(maxBytes) { index ->
            val byte = unsignedByte()
            value =
                value or
                    (byte and MessageWriter.VARINT_PAYLOAD shl (index * MessageWriter.VARINT_SHIFT))
            if (byte and MessageWriter.VARINT_CONTINUE == 0) return value
        }
        throw MalformedMessageException("Varint longer than $maxBytes bytes")
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
        if (count < 0 || count > max)
            throw MalformedMessageException("List of $count elements exceeds $max")
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
        get() = !buffer.hasRemaining()

    private inline fun <T> read(block: ByteBuffer.() -> T): T =
        try {
            buffer.block()
        } catch (_: BufferUnderflowException) {
            throw MalformedMessageException(
                "Payload truncated at byte ${buffer.position()} of ${buffer.limit()}"
            )
        }

    private companion object {
        const val MAX_UTF8_BYTES_PER_CHAR = 3
        const val MAX_VARINT_BYTES = 5

        /** FML's string length prefix, kept so GTNH peers interoperate. */
        const val MAX_STRING_VARINT_BYTES = 2
    }
}
