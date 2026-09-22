package io.github.fopwoc.mods.framework.network

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Big-endian writes matching [MessageReader], and the wire format of Netty's `ByteBuf` defaults,
 * so a payload reads the same on every loader.
 */
class MessageWriter {
    private val bytes = ByteArrayOutputStream()
    private val output = DataOutputStream(bytes)

    fun boolean(value: Boolean) = apply { output.writeBoolean(value) }

    fun byte(value: Int) = apply { output.writeByte(value) }

    fun short(value: Int) = apply { output.writeShort(value) }

    fun int(value: Int) = apply { output.writeInt(value) }

    fun long(value: Long) = apply { output.writeLong(value) }

    fun float(value: Float) = apply { output.writeFloat(value) }

    fun double(value: Double) = apply { output.writeDouble(value) }

    /** Varint byte length + UTF-8, truncated to [maxLength] chars; [MessageReader.utf8] reads it. */
    fun utf8(value: String, maxLength: Int) = apply {
        val encoded = value.take(maxLength).toByteArray(Charsets.UTF_8)
        varInt(encoded.size)
        output.write(encoded)
    }

    /** Ordinal as one unsigned byte; [MessageReader.enum] reads it. */
    fun enum(value: Enum<*>) = byte(value.ordinal)

    fun varInt(value: Int) = apply {
        var remaining = value
        while (remaining and VARINT_PAYLOAD.inv() != 0) {
            output.writeByte(remaining and VARINT_PAYLOAD or VARINT_CONTINUE)
            remaining = remaining ushr VARINT_SHIFT
        }
        output.writeByte(remaining)
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()

    internal companion object {
        const val VARINT_PAYLOAD = 0x7F
        const val VARINT_CONTINUE = 0x80
        const val VARINT_SHIFT = 7
    }
}
