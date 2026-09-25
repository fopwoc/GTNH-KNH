package io.github.fopwoc.knhmp

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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
 *
 * The holder writes who it is into the lock file (the locks are advisory, so it stays readable),
 * and a waiting build says who it waits for, every [REPORT_INTERVAL].
 */
abstract class KnhMpIslandLocks : BuildService<BuildServiceParameters.None>, AutoCloseable {
    private val logger = Logging.getLogger(KnhMpIslandLocks::class.java)
    private val held = ConcurrentHashMap<File, Pair<RandomAccessFile, FileLock>>()

    fun acquire(lockFile: File, taskPath: String) {
        lockFile.parentFile.mkdirs()
        val file = RandomAccessFile(lockFile, "rw")
        val lock = file.channel.tryLock() ?: waitFor(file, lockFile)
        file.setLength(0)
        file.write(ownerDescription(taskPath).toByteArray())
        held[lockFile] = file to lock
    }

    fun release(lockFile: File) {
        held.remove(lockFile)?.let { (file, lock) ->
            file.setLength(0)
            lock.release()
            file.close()
        }
    }

    override fun close() = held.keys.toList().forEach(::release)

    private fun waitFor(file: RandomAccessFile, lockFile: File): FileLock {
        val island = lockFile.parentFile.parentFile.name
        val started = System.nanoTime()
        var nextReport = Duration.ZERO
        while (true) {
            file.channel.tryLock()?.let {
                return it
            }
            val waited = Duration.ofNanos(System.nanoTime() - started)
            if (waited >= nextReport) {
                logger.lifecycle(
                    "Waiting {}s for the {} island, held by {}",
                    waited.seconds,
                    island,
                    holderOf(lockFile),
                )
                nextReport += REPORT_INTERVAL
            }
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun holderOf(lockFile: File): String =
        runCatching { lockFile.readText().trim() }.getOrNull()?.takeIf(String::isNotEmpty)
            ?: "another Gradle process that did not say which"

    private fun ownerDescription(taskPath: String): String {
        val process = ProcessHandle.current()
        val kind =
            when {
                System.getProperty("idea.sync.active") == "true" -> "IntelliJ Gradle sync"
                System.getProperty("idea.active") == "true" -> "a Gradle build started by IntelliJ"
                else -> "a command-line Gradle build"
            }
        val since = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        return "$kind (pid ${process.pid()}, $taskPath, since $since)"
    }

    private companion object {
        val REPORT_INTERVAL: Duration = Duration.ofSeconds(30)
        const val POLL_MILLIS = 500L
    }
}

/** Runs [GradleBuild] under the island's lock. */
internal fun GradleBuild.lockIsland(lockFile: File) {
    val locks =
        project.gradle.sharedServices.registerIfAbsent(
            "knhmpIslandLocks",
            KnhMpIslandLocks::class.java,
        ) {}
    usesService(locks)
    val taskPath = path
    doFirst { locks.get().acquire(lockFile, taskPath) }
    doLast { locks.get().release(lockFile) }
}
