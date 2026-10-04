package io.github.fopwoc.palimpsest.db.store

import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Positional write of all of [buffer]; positional I/O shares no file pointer, so threads may
 * overlap.
 */
internal fun FileChannel.writeFully(buffer: ByteBuffer, position: Long) {
    var at = position
    while (buffer.hasRemaining()) at += write(buffer, at)
}

/** Positional read of exactly [length] bytes, or [EOFException] if the file ends first. */
internal fun FileChannel.readFully(position: Long, length: Int): ByteArray {
    val buffer = ByteBuffer.allocate(length)
    var at = position
    while (buffer.hasRemaining()) {
        val read = read(buffer, at)
        if (read < 0) throw EOFException("File ends at $at, needed ${position + length}")
        at += read
    }
    return buffer.array()
}
