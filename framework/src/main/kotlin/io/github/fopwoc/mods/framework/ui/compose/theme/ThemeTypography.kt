package io.github.fopwoc.mods.framework.ui.compose.theme

import androidx.compose.runtime.Immutable
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle

/** Text roles of [MinecraftTheme]. Vanilla has one font, so roles differ by colour and wrapping. */
@Immutable
data class ThemeTypography(
    val title: TextStyle,
    val sectionTitle: TextStyle,
    val body: TextStyle,
    val muted: TextStyle,
) {
  companion object {
    fun from(colors: ThemeColors): ThemeTypography =
        ThemeTypography(
            title = TextStyle(color = colors.foreground),
            sectionTitle = TextStyle(color = colors.title),
            body = TextStyle(color = colors.foreground),
            muted = TextStyle(color = colors.muted),
        )

    val Default = from(ThemeColors.Default)
  }
}
