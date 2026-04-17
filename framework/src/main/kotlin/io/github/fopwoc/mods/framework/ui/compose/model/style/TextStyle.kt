package io.github.fopwoc.mods.framework.ui.compose.model.style

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment

data class TextStyle(
    val color: Int = 0xE6E6E6,
    val shadow: Boolean = true,
    val alignment: HorizontalAlignment = HorizontalAlignment.START,
    val wrap: Boolean = false
)


