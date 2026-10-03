package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

/** Nanosecond samples summarized in milliseconds. */
class Timings(samples: Collection<Long>) {
    private val sorted = samples.sorted()

    fun at(quantile: Double): Double =
        if (sorted.isEmpty()) 0.0
        else sorted[((sorted.size - 1) * quantile).toInt()] / 1_000_000.0

    val total: Double
        get() = sorted.sum() / 1_000_000.0

    override fun toString(): String =
        "p50 %.3f  p95 %.3f  p99 %.3f  max %.3f ms".format(at(0.5), at(0.95), at(0.99), at(1.0))
}

inline fun <T> timed(samples: MutableList<Long>, block: () -> T): T {
    val start = System.nanoTime()
    return block().also { samples += System.nanoTime() - start }
}
