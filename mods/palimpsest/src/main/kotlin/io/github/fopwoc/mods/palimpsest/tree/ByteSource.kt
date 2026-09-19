package io.github.fopwoc.mods.palimpsest.tree

/** Cursor over encoded bytes; the mirror of [ByteSink]. Throws [CorruptTreeException] past the end. */
class ByteSource(private val bytes: ByteArray, from: Int = 0, private val to: Int = bytes.size) {
    var position = from
        private set

    val remaining: Int
        get() = to - position

    fun byte(): Int {
        if (position >= to) throw CorruptTreeException("Record truncated at $position")
        return bytes[position++].toInt() and 0xFF
    }

    fun bytes(count: Int): ByteArray {
        if (count < 0 || position + count > to) throw CorruptTreeException("Record truncated at $position")
        return bytes.copyOfRange(position, position + count).also { position += count }
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
        if (value > Int.MAX_VALUE) throw CorruptTreeException("Varint out of int range at $position")
        return value.toInt()
    }

    fun signed(): Long {
        val raw = varint()
        return (raw ushr 1) xor -(raw and 1)
    }

    fun skip(count: Int) {
        if (count < 0 || position + count > to) throw CorruptTreeException("Record truncated at $position")
        position += count
    }
}
