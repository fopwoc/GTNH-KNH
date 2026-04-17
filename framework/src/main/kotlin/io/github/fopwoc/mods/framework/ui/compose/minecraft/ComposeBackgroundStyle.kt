package io.github.fopwoc.mods.framework.ui.compose.minecraft

sealed class ComposeBackgroundStyle {
    data class Color(val argb: Int) : ComposeBackgroundStyle()

    object VanillaDefault : ComposeBackgroundStyle()

    object None : ComposeBackgroundStyle()
}

