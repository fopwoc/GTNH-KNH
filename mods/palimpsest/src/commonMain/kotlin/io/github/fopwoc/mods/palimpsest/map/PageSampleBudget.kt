package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.render.SampleGrid
import java.util.LinkedHashMap

/** Shared byte limit for cached terrain facts; eviction preserves every cached raster. */
class PageSampleBudget(val maxBytes: Long = 24L shl 20) {
    init {
        require(maxBytes >= 0)
    }

    private val lock = Any()
    private val samples = LinkedHashMap<Lease, SampleGrid>(64, 0.75f, true)
    private var bytes = 0L

    val retainedBytes: Long
        get() = synchronized(lock) { bytes }

    internal inner class Lease {
        fun get(): SampleGrid? = synchronized(lock) { samples[this] }

        fun release() =
            synchronized(lock) {
                samples.remove(this)?.let { bytes -= it.bytes }
                Unit
            }
    }

    internal fun retain(grid: SampleGrid): Lease =
        synchronized(lock) {
            val lease = Lease()
            if (grid.bytes <= maxBytes) {
                samples[lease] = grid
                bytes += grid.bytes
                while (bytes > maxBytes) {
                    val entries = samples.entries.iterator()
                    val oldest = entries.next()
                    bytes -= oldest.value.bytes
                    entries.remove()
                }
            }
            lease
        }
}
