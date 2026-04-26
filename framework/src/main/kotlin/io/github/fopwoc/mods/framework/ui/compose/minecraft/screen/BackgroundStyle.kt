package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color as UiColor

sealed class ComposeBackgroundStyle {
    data class Color(val color: UiColor) : ComposeBackgroundStyle()

    object VanillaDefault : ComposeBackgroundStyle()

    object None : ComposeBackgroundStyle()
}

