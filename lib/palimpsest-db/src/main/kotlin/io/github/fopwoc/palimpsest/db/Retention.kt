package io.github.fopwoc.palimpsest.db

/**
 * How much history a dimension keeps. It only steers compaction: every commit is written the same
 * way, and what the dimension lets go is dropped when it is next compacted.
 */
enum class Retention {
    /** Every committed version. */
    HISTORY,

    /**
     * Only the latest version of each chunk survives compaction; history before it is gone for
     * good.
     */
    LATEST,
}
