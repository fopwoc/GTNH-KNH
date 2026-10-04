package io.github.fopwoc.palimpsest.db

/** How much of a dimension the database keeps: [depth] in space, [time] in history. */
data class DimensionMode(val depth: Depth, val time: Retention)

enum class Depth {
    /** Only the 2D summary of each column. */
    SURFACE,

    /** Every block. */
    VOLUME,
}

enum class Retention {
    /** Only the latest version of each chunk survives compaction. */
    LATEST,

    /** Every committed version. */
    HISTORY,
}
