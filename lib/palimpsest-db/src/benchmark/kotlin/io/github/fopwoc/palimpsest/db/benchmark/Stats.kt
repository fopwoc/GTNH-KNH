package io.github.fopwoc.palimpsest.db.benchmark

/** Duration samples in nanoseconds. */
class Stats {
    private val samples = ArrayList<Long>()

    fun add(nanos: Long) {
        samples += nanos
    }

    val total: Long
        get() = samples.sum()

    val count: Int
        get() = samples.size

    fun mean(): Double = if (samples.isEmpty()) 0.0 else total.toDouble() / samples.size

    fun percentile(p: Double): Long {
        if (samples.isEmpty()) return 0
        val sorted = samples.sorted()
        return sorted[((sorted.size - 1) * p).toInt()]
    }
}

inline fun timed(block: () -> Unit): Long {
    val start = System.nanoTime()
    block()
    return System.nanoTime() - start
}

fun bytes(value: Double): String =
    when {
        value >= 1 shl 30 -> "%.2f GiB".format(value / (1 shl 30))
        value >= 1 shl 20 -> "%.1f MiB".format(value / (1 shl 20))
        value >= 1 shl 10 -> "%.1f KiB".format(value / (1 shl 10))
        else -> "%.0f B".format(value)
    }

fun millis(nanos: Double): String = "%.2f ms".format(nanos / 1e6)

fun micros(nanos: Double): String = "%.0f µs".format(nanos / 1e3)
