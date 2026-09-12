package io.github.fopwoc.mods.hotspot.client.overlay

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color

/** Green → yellow → red for a 0..1 share of the heaviest item in view. */
object HeatScale {
  fun color(fraction: Double, alpha: Int = 255): Color {
    val f = fraction.coerceIn(0.0, 1.0)
    val red: Int
    val green: Int
    if (f < 0.5) {
      red = (f * 2.0 * 255).toInt()
      green = 220
    } else {
      red = 255
      green = (220 * (1.0 - (f - 0.5) * 2.0)).toInt()
    }
    return Color.argb(alpha, red, green, 60)
  }
}
