package io.github.fopwoc.mods.framework.ui.compose.runtime

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreen

val LocalComposeGuiScreen = staticCompositionLocalOf<ComposeGuiScreen> {
    error("ComposeGuiScreen context is not available")
}

