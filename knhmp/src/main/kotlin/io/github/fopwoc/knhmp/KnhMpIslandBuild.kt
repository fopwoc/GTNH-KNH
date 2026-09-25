package io.github.fopwoc.knhmp

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.util.concurrent.ConcurrentHashMap
import org.gradle.api.logging.Logging
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.GradleBuild

/**
 * Exclusive file locks on islands for the duration of their nested builds. IntelliJ's sync daemon
 * and a command-line build are separate processes that would otherwise compile the same island at
 * once and corrupt its jars and Kotlin caches. Locks still held when the build ends, for example
 * after a failed nested build, are released with the service.
 */
abstract class KnhMpIslandLocks : BuildService<BuildServiceParameters.None>, AutoCloseable {
    private val logger = Logging.getLogger(KnhMpIslandLocks::class.java)
    private val held = ConcurrentHashMap<File, Pair<RandomAccessFile, FileLock>>()

    fun acquire(lockFile: File) {
        lockFile.parentFile.mkdirs()
        val file = RandomAccessFile(lockFile, "rw")
        val lock =
            file.channel.tryLock()
                ?: run {
                    logger.lifecycle(
                        "Waiting for another Gradle process to finish with ${lockFile.parentFile.parentFile.name}"
                    )
                    file.channel.lock()
                }
        held[lockFile] = file to lock
    }

    fun release(lockFile: File) {
        held.remove(lockFile)?.let { (file, lock) ->
            lock.release()
            file.close()
        }
    }

    override fun close() = held.keys.toList().forEach(::release)
}

/** Runs [GradleBuild] under the island's lock. */
internal fun GradleBuild.lockIsland(lockFile: File) {
    val locks =
        project.gradle.sharedServices.registerIfAbsent(
            "knhmpIslandLocks",
            KnhMpIslandLocks::class.java,
        ) {}
    usesService(locks)
    doFirst { locks.get().acquire(lockFile) }
    doLast { locks.get().release(lockFile) }
}
