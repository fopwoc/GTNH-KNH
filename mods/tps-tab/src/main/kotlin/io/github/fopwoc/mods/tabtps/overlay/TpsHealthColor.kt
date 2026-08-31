package io.github.fopwoc.mods.tabtps.overlay

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import kotlin.math.roundToInt

internal object TpsHealthColor {
  fun forTps(tps: Double): Color {
    val normalizedTps = tps.takeIf(Double::isFinite) ?: 0.0
    return when {
      normalizedTps >= YELLOW_TPS -> interpolate(YELLOW, GREEN, normalizedTps - YELLOW_TPS)
      else -> interpolate(RED, YELLOW, normalizedTps - RED_TPS)
    }
  }

  private fun interpolate(start: Color, end: Color, progress: Double): Color {
    val fraction = progress.coerceIn(0.0, 1.0)
    return Color.rgb(
        red = channel(start.red, end.red, fraction),
        green = channel(start.green, end.green, fraction),
        blue = channel(start.blue, end.blue, fraction),
    )
  }

  private fun channel(start: Int, end: Int, progress: Double): Int =
      (start + (end - start) * progress).roundToInt()

  private const val RED_TPS = 18.0
  private const val YELLOW_TPS = 19.0

  private val GREEN = Color.rgb(red = 0x55, green = 0xFF, blue = 0x55)
  private val YELLOW = Color.rgb(red = 0xFF, green = 0xD5, blue = 0x4A)
  private val RED = Color.rgb(red = 0xFF, green = 0x55, blue = 0x55)
}
