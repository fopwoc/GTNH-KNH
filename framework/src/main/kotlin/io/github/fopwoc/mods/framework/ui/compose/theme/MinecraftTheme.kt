package io.github.fopwoc.mods.framework.ui.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalThemeColors = staticCompositionLocalOf { ThemeColors.Default }
val LocalThemeTypography = staticCompositionLocalOf { ThemeTypography.Default }

/**
 * `MaterialTheme` for Minecraft: ambient colour and text roles read from composition locals. Every
 * screen and HUD gets the defaults for free; wrap a subtree in [MinecraftTheme] to change them.
 *
 * ```kotlin
 * Text("Hint", style = MinecraftTheme.typography.muted)
 * Panel(borderColor = MinecraftTheme.colors.surfaceBorder) { … }
 *
 * MinecraftTheme(colors = MinecraftTheme.colors.copy(accent = Color(0xFFFF8080))) { … }
 * ```
 */
object MinecraftTheme {
  val colors: ThemeColors
    @Composable @ReadOnlyComposable get() = LocalThemeColors.current

  val typography: ThemeTypography
    @Composable @ReadOnlyComposable get() = LocalThemeTypography.current
}

@Composable
fun MinecraftTheme(
    colors: ThemeColors = MinecraftTheme.colors,
    typography: ThemeTypography = ThemeTypography.from(colors),
    content: @Composable () -> Unit,
) {
  CompositionLocalProvider(
      LocalThemeColors provides colors,
      LocalThemeTypography provides typography,
      content = content,
  )
}
