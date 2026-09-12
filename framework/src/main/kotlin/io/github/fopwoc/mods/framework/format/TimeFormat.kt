package io.github.fopwoc.mods.framework.format

import java.util.Locale

object TimeFormat {
  /** Fixed unit and two decimals: `12.34 ms`. Stable width for tables and HUD rows. */
  fun millis(value: Double): String = String.format(Locale.ROOT, "%.2f ms", value)

  /** Unit picked by magnitude: `123 ms`, `4.13 ms`, `410 µs`, `0 µs`. */
  fun millisAdaptive(value: Double): String =
      when {
        value >= 100.0 -> String.format(Locale.ROOT, "%.0f ms", value)
        value >= 1.0 -> String.format(Locale.ROOT, "%.2f ms", value)
        value >= 0.001 -> String.format(Locale.ROOT, "%.0f µs", value * 1000.0)
        else -> "0 µs"
      }
}
