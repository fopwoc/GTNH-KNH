package io.github.fopwoc.mods.framework.ui.compose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState

@Composable
fun rememberScrollState(initial: Int = 0): ScrollState =
    rememberSaveable(initial, saver = ScrollState.Saver) {
      ScrollState(initial = initial)
    }

@Composable
fun rememberTextFieldState(initialText: String = ""): TextFieldState = remember {
  TextFieldState(initialText = initialText)
}
