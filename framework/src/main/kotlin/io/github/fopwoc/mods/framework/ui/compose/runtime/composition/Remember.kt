package io.github.fopwoc.mods.framework.ui.compose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState

/**
 * Screens do not provide a `SaveableStateRegistry`, so at the screen root this behaves like
 * [remember]; inside a [io.github.fopwoc.mods.framework.ui.compose.navigation.NavHost] entry the
 * scroll position survives navigating away and back.
 */
@Composable
fun rememberScrollState(initial: Int = 0): ScrollState =
    rememberSaveable(initial, saver = ScrollState.Saver) {
      ScrollState(initial = initial)
    }

@Composable
fun rememberTextFieldState(initialText: String = ""): TextFieldState = remember {
  TextFieldState(initialText = initialText)
}
