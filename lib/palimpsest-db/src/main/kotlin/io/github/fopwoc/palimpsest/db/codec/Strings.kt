package io.github.fopwoc.palimpsest.db.codec

/** A UTF-8 string with its byte length as a varint. */
internal fun ByteSink.string(value: String) {
    val bytes = value.encodeToByteArray()
    varint(bytes.size)
    bytes(bytes)
}

internal fun ByteSource.string(): String = bytes(varintInt()).decodeToString()
