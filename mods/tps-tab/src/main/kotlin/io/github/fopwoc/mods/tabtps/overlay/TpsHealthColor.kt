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

  /** Colour for a tick cost on its own: the rate this cost alone would allow. */
  fun forMspt(mspt: Double): Color =
      forTps(if (mspt <= 0.0) 20.0 else kotlin.math.min(20.0, 1000.0 / mspt))

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

  private val GREEN = Color(0xFF55FF55)
  private val YELLOW = Color(0xFFFFD54A)
  private val RED = Color(0xFFFF5555)
}
