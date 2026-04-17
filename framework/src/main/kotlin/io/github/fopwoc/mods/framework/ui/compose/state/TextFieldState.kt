package io.github.fopwoc.mods.framework.ui.compose.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
class TextFieldState(initialText: String = "") {
    var text by mutableStateOf(initialText)
    var focused by mutableStateOf(false)
        private set

    fun requestFocus() {
        focused = true
    }

    fun clearFocus() {
        focused = false
    }

    internal fun syncFocus(focused: Boolean) {
        this.focused = focused
    }
}

