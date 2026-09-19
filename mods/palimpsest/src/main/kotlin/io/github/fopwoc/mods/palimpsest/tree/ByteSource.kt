package io.github.fopwoc.mods.palimpsest.tree

import java.nio.ByteBuffer

/**
 * Cursor over encoded bytes, heap or memory-mapped; the mirror of [ByteSink]. Reads past [limit] throw
 * [CorruptTreeException], so a damaged record can never turn into an out-of-bounds read.
 */
class ByteSource(
    val buffer: ByteBuffer,
    from: Int = 0,
    val limit: Int = buffer.limit(),
) {
    constructor(
        bytes: ByteArray,
        from: Int = 0,
        to: Int = bytes.size,
    ) : this(ByteBuffer.wrap(bytes), from, to)

    var position = from
        private set

    val remaining: Int
        get() = limit - position

    fun byte(): Int {
        if (position >= limit) throw CorruptTreeException("Record truncated at $position")
        return buffer.get(position++).toInt() and 0xFF
    }

    fun bytes(count: Int): ByteArray {
        if (count < 0 || position + count > limit)
            throw CorruptTreeException("Record truncated at $position")
        val bytes = ByteArray(count)
        buffer.get(position, bytes)
        position += count
        return bytes
    }

    fun fixed(width: Int): Long {
        var value = 0L
        repeat(width) { value = (value shl 8) or byte().toLong() }
        return value
    }

    fun varint(): Long {
        var value = 0L
        var shift = 0
        while (true) {
            val byte = byte()
            value = value or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return value
            shift += 7
            if (shift > 63) throw CorruptTreeException("Varint too long at $position")
        }
    }

    fun varintInt(): Int {
        val value = varint()
        if (value > Int.MAX_VALUE)
            throw CorruptTreeException("Varint out of int range at $position")
        return value.toInt()
    }

    fun signed(): Long {
        val raw = varint()
        return (raw ushr 1) xor -(raw and 1)
    }

    fun skip(count: Int) {
        if (count < 0 || position + count > limit)
            throw CorruptTreeException("Record truncated at $position")
        position += count
    }
}
