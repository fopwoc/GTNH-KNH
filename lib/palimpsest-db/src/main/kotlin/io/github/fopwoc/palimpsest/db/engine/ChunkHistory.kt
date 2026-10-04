package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.store.BlobRef

/**
 * Every version of one chunk. [versions] is what readers see: replaced whole by the writer when a
 * commit is published. [prepared] runs ahead of it: the version the commit pipeline compares the
 * next observation with, possibly not written yet.
 */
internal class ChunkHistory {
    class Version(val tick: Long, val minSection: Int, val slots: Array<BlobRef?>)

    @Volatile private var versions: Array<Version> = emptyArray()

    /**
     * Touched only by the pipeline stage handling this chunk; stages of one dimension never
     * overlap.
     */
    var prepared: Version? = null

    val latest: Version?
        get() = versions.lastOrNull()

    /** The last version at or before [tick]. */
    fun at(tick: Long): Version? {
        val all = versions
        var low = 0
        var high = all.size - 1
        var found: Version? = null
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (all[middle].tick <= tick) {
                found = all[middle]
                low = middle + 1
            } else high = middle - 1
        }
        return found
    }

    /** Writer thread only. */
    fun publish(version: Version) {
        versions += version
    }

    /** A version read back from disk: both published and the base for the next comparison. */
    fun restore(version: Version) {
        publish(version)
        prepared = version
    }
}
