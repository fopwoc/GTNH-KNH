package io.github.fopwoc.palimpsest.db

import java.nio.file.Path

/**
 * Everything the database needs from its host. [cacheDirectory] holds rebuildable indexes outside
 * the synced world folder; the thread counts size the read and background pools.
 */
class DbConfig(
    val cacheDirectory: Path,
    val blockKinds: BlockKinds,
    val log: DbLog,
    val interactiveThreads: Int = defaultThreads(),
    val backgroundThreads: Int = defaultThreads(),
) {
    init {
        require(interactiveThreads > 0 && backgroundThreads > 0) {
            "Pools need at least one thread"
        }
    }

    private companion object {
        /** Every core but the ones the game's own threads keep busy. */
        fun defaultThreads(): Int =
            (Runtime.getRuntime().availableProcessors() - 2).coerceAtLeast(1)
    }
}
