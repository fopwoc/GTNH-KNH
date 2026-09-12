package io.github.fopwoc.mods.framework.ui.compose.theme

import androidx.compose.runtime.Immutable
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color

/** Colour roles of [MinecraftTheme]; the defaults are the dark look shared by the KNH menus. */
@Immutable
data class ThemeColors(
    val foreground: Color = Color(0xFFE6E6E6),
    val muted: Color = Color(0xFFBCBCBC),
    val title: Color = Color(0xFFFFD54A),
    val accent: Color = Color(0xFF8FD0FF),
    val success: Color = Color(0xFF9AE28D),
    val warning: Color = Color(0xFFFFB45A),
    val danger: Color = Color(0xFFFFAAAA),
    val shellBackground: Color = Color(0xB0141418),
    val shellBorder: Color = Color(0xFF4A4A56),
    val surfaceBackground: Color = Color(0x7A101216),
    val surfaceBorder: Color = Color(0xFF343844),
    val elevatedBackground: Color = Color(0x60303743),
    val chipBackground: Color = Color(0xCC2A2D34),
    val chipBorder: Color = Color(0xFF5A5E68),
) {
  companion object {
    val Default = ThemeColors()
  }
}
