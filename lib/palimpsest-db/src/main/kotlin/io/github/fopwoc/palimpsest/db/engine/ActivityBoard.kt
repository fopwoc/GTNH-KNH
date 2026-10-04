package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.Activity
import io.github.fopwoc.palimpsest.db.DimensionId
import java.util.concurrent.atomic.AtomicLong

/**
 * Running tasks with live progress. Tasks advance from any thread; a snapshot costs a short lock
 * and one object per running task, cheap enough to take every frame.
 */
internal class ActivityBoard {
    inner class Handle
    internal constructor(
        private val task: Activity.Task,
        private val dimension: DimensionId?,
        private val total: Long?,
    ) : AutoCloseable {
        private val done = AtomicLong()

        fun advance(amount: Long) {
            done.addAndGet(amount)
        }

        internal fun snapshot() = Activity(task, dimension, done.get(), total)

        override fun close() {
            synchronized(running) { running.remove(this) }
        }
    }

    private val running = LinkedHashSet<Handle>()

    fun start(task: Activity.Task, dimension: DimensionId?, total: Long?): Handle =
        Handle(task, dimension, total).also { synchronized(running) { running += it } }

    fun snapshot(): List<Activity> = synchronized(running) { running.map(Handle::snapshot) }
}
