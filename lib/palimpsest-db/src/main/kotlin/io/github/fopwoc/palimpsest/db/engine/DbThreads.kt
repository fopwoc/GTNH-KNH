package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.DbConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The database's threads, all daemons named `palimpsest-db-*`: one writer that owns every file
 * append, a background pool for comparing and encoding, and an interactive pool for reads.
 */
internal class DbThreads(config: DbConfig) : AutoCloseable {
    val writer: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(factory("writer", Thread.NORM_PRIORITY))
    val background: ExecutorService =
        Executors.newFixedThreadPool(
            config.backgroundThreads,
            factory("background", Thread.MIN_PRIORITY),
        )
    val interactive: ExecutorService =
        Executors.newFixedThreadPool(
            config.interactiveThreads,
            factory("read", Thread.NORM_PRIORITY),
        )

    /** Lets queued writes finish, then stops everything. */
    override fun close() {
        writer.shutdown()
        writer.awaitTermination(1, TimeUnit.MINUTES)
        background.shutdownNow()
        interactive.shutdownNow()
    }

    private fun factory(role: String, priority: Int): ThreadFactory {
        val count = AtomicInteger()
        return ThreadFactory { task ->
            Thread(task, "palimpsest-db-$role-${count.incrementAndGet()}").apply {
                isDaemon = true
                this.priority = priority
            }
        }
    }
}
