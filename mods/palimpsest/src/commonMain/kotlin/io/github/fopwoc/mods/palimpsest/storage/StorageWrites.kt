package io.github.fopwoc.mods.palimpsest.storage

import java.util.concurrent.atomic.LongAdder

/** Application-issued data bytes, including replaced files; not filesystem or NAND telemetry. */
class StorageWrites {
    enum class Kind {
        HISTORY,
        MANIFEST,
        CURRENT_APPEND,
        CURRENT_COMPACTION,
        VOCABULARY,
    }

    class Snapshot(val bytes: Map<Kind, Long>, val flushes: Long, val compactions: Long) {
        val totalBytes: Long = bytes.values.sum()
    }

    private val bytes = Array(Kind.entries.size) { LongAdder() }
    private val flushes = LongAdder()
    private val compactions = LongAdder()

    fun written(kind: Kind, count: Long) {
        require(count >= 0)
        bytes[kind.ordinal].add(count)
    }

    fun flushed() = flushes.increment()

    fun compacted() = compactions.increment()

    /** Read after writers quiesce for a consistent multi-counter benchmark snapshot. */
    fun snapshot(): Snapshot =
        Snapshot(
            Kind.entries.associateWith { bytes[it.ordinal].sum() },
            flushes.sum(),
            compactions.sum(),
        )
}
