package io.github.fopwoc.palimpsest.db.store

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

/**
 * One writer per world, enforced by the OS: the lock dies with the process, so a crash never leaves
 * a stale flag behind, and the file's text only tells the next opener who holds it.
 */
internal class WorldLock
private constructor(private val channel: FileChannel, private val lock: FileLock) : AutoCloseable {
    override fun close() {
        lock.release()
        channel.close()
    }

    companion object {
        /** The lock, or null when another process or another open in this one holds it. */
        fun tryAcquire(path: Path): WorldLock? {
            val channel = FileChannel.open(path, CREATE, WRITE)
            val lock =
                try {
                    channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                }
            if (lock == null) {
                channel.close()
                return null
            }
            val holder = "pid ${ProcessHandle.current().pid()} since ${java.time.Instant.now()}"
            channel.truncate(0)
            channel.writeFully(ByteBuffer.wrap(holder.encodeToByteArray()), 0)
            return WorldLock(channel, lock)
        }

        fun holder(path: Path): String = runCatching {
            Files.readString(path)
        }
            .getOrDefault("")
            .ifBlank { "unknown" }
    }
}
