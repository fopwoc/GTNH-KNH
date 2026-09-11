package io.github.fopwoc.mods.framework.ui.compose.layout.render

import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState

/** Focus bookkeeping a text field needs from its host; HUD overlays use [None]. */
internal interface TextFieldHost {
  fun rendered(state: TextFieldState, maxLength: Int)

  fun focus(state: TextFieldState)

  object None : TextFieldHost {
    override fun rendered(state: TextFieldState, maxLength: Int) = Unit

    override fun focus(state: TextFieldState) = Unit
  }
}
