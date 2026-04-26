package io.github.fopwoc.mods.framework.ui.compose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState

@Composable
fun rememberScrollState(initial: Int = 0): ScrollState = remember {
    ScrollState(initial = initial)
}

@Composable
fun rememberTextFieldState(initialText: String = ""): TextFieldState = remember {
    TextFieldState(initialText = initialText)
}

