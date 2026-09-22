package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Lives in the screen's ViewModelStore: survives switching stories, dies with the screen. */
class CounterViewModel : ViewModel() {
    val count = MutableStateFlow(0)

    fun add(delta: Int) = count.update { it + delta }
}
