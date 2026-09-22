package io.github.fopwoc.mods.palimpsest.tree

/**
 * Growable byte buffer for encoding records: raw bytes, fixed-width integers and LEB128 varints.
 */
class ByteSink(initialCapacity: Int = 256) {
    private var bytes = ByteArray(initialCapacity)
    var size = 0
        private set

    fun byte(value: Int) {
        ensure(1)
        bytes[size++] = value.toByte()
    }

    fun bytes(source: ByteArray, from: Int = 0, to: Int = source.size) {
        ensure(to - from)
        source.copyInto(bytes, size, from, to)
        size += to - from
    }

    /** Big-endian, low [width] bytes of [value]. */
    fun fixed(value: Long, width: Int) {
        ensure(width)
        for (shift in (width - 1) downTo 0) bytes[size++] = (value ushr (shift * 8)).toByte()
    }

    fun varint(value: Long) {
        require(value >= 0)
        var rest = value
        while (rest >= 0x80) {
            byte((rest and 0x7F).toInt() or 0x80)
            rest = rest ushr 7
        }
        byte(rest.toInt())
    }

    fun varint(value: Int) = varint(value.toLong())

    /** Signed varint, zigzag-mapped so small negatives stay one byte. */
    fun signed(value: Long) = varint((value shl 1) xor (value shr 63))

    fun toByteArray(): ByteArray = bytes.copyOf(size)

    fun clear() {
        size = 0
    }

    private fun ensure(extra: Int) {
        if (size + extra > bytes.size) bytes = bytes.copyOf(maxOf(bytes.size * 2, size + extra))
    }
}
