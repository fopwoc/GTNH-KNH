package io.github.fopwoc.mods.framework.format

import java.util.Locale

object TimeFormat {
    private const val WHOLE_MILLIS_THRESHOLD = 100.0
    private const val MICROS_PER_MILLI = 1000.0
    private const val ONE_MICRO_IN_MILLIS = 1.0 / MICROS_PER_MILLI

    /** Fixed unit and two decimals: `12.34 ms`. Stable width for tables and HUD rows. */
    fun millis(value: Double): String = String.format(Locale.ROOT, "%.2f ms", value)

    /** Unit picked by magnitude: `123 ms`, `4.13 ms`, `410 µs`, `0 µs`. */
    fun millisAdaptive(value: Double): String =
        when {
            value >= WHOLE_MILLIS_THRESHOLD -> String.format(Locale.ROOT, "%.0f ms", value)
            value >= 1.0 -> String.format(Locale.ROOT, "%.2f ms", value)
            value >= ONE_MICRO_IN_MILLIS ->
                String.format(Locale.ROOT, "%.0f µs", value * MICROS_PER_MILLI)
            else -> "0 µs"
        }
}
