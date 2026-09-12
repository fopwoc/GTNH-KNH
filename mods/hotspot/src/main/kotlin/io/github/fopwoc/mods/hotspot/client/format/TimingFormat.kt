package io.github.fopwoc.mods.hotspot.client.format

import java.util.Locale

object TimingFormat {
  /** `4.12 ms`, `410 µs`, `12 µs`. */
  fun ms(value: Double): String =
      when {
        value >= 100.0 -> String.format(Locale.ROOT, "%.0f ms", value)
        value >= 1.0 -> String.format(Locale.ROOT, "%.2f ms", value)
        value >= 0.001 -> String.format(Locale.ROOT, "%.0f µs", value * 1000.0)
        else -> "0 µs"
      }

  fun chunk(chunkX: Int, chunkZ: Int): String = "($chunkX, $chunkZ)"

  fun block(x: Int, y: Int, z: Int): String = "$x $y $z"
}
