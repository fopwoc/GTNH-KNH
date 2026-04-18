package io.github.fopwoc.mods.framework.ui.compose.model.style

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color

data class TextFieldStyle(
    val maxLength: Int = 256,
    val textColor: Color = Color.rgb(red = 0xE0, green = 0xE0, blue = 0xE0),
    val disabledTextColor: Color = Color.rgb(red = 0x70, green = 0x70, blue = 0x70),
    val drawBackground: Boolean = true
)


