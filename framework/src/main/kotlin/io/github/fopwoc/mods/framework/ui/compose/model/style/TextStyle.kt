package io.github.fopwoc.mods.framework.ui.compose.model.style

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color

data class TextStyle(
    val color: Color = Color.rgb(red = 0xE6, green = 0xE6, blue = 0xE6),
    val shadow: Boolean = true,
    val alignment: HorizontalAlignment = HorizontalAlignment.START,
    val wrap: Boolean = false,
)
