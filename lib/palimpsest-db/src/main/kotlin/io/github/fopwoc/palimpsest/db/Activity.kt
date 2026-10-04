package io.github.fopwoc.palimpsest.db

/**
 * Something the database is doing in the background, for the UI to show: what, where, and how far
 * along. [done] and [total] count the task's own units (bytes of history, chunks of a commit); a
 * null [total] means there is no estimate, only that it runs.
 */
class Activity
internal constructor(
    val task: Task,
    val dimension: DimensionId?,
    val done: Long,
    val total: Long?,
) {
    enum class Task {
        /**
         * Reading a dimension's history into a fresh index: after a format change, a lost cache, a
         * new computer.
         */
        REBUILDING_INDEX,

        /**
         * Applying history the index has not seen yet: after a crash or another computer's session.
         */
        CATCHING_UP_INDEX,

        /**
         * Rewriting a dimension's history into one segment, dropping what its retention lets go.
         */
        COMPACTING,

        /** Moving the index along with a compaction, region by region, instead of rebuilding it. */
        REMAPPING_INDEX,

        /** Comparing and encoding the chunks of a commit. */
        COMMITTING,

        /** Making what was committed durable. */
        FLUSHING,
    }

    /** Progress in 0..1, or null without an estimate. */
    val fraction: Float?
        get() = total?.takeIf { it > 0 }?.let { (done.toFloat() / it).coerceIn(0f, 1f) }

    override fun toString(): String = "$task ${dimension?.key.orEmpty()} $done/${total ?: "?"}"
}
