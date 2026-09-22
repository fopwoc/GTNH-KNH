package io.github.fopwoc.mods.framework.ui.compose.model.style

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color

data class TextFieldStyle(
    val maxLength: Int = 256,
    val textColor: Color = Color(0xFFE0E0E0),
    val disabledTextColor: Color = Color(0xFF707070),
    val drawBackground: Boolean = true,
)
