package io.github.fopwoc.mods.testgui.client.gui.ui.screens.navigation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class NavigationViewModel : ViewModel() {
    val stateFlow = MutableStateFlow(NavigationModel())

    fun recordSelfPush() {
        stateFlow.update { state ->
            state.copy(
                selfPushes = state.selfPushes + 1,
                outerEvents = prepend("Pushed another Navigation entry on the outer stack", state.outerEvents)
            )
        }
    }

    fun recordOuterEvent(message: String) {
        stateFlow.update { state ->
            state.copy(outerEvents = prepend(message, state.outerEvents))
        }
    }

    fun recordInnerEvent(message: String) {
        stateFlow.update { state ->
            state.copy(innerEvents = prepend(message, state.innerEvents))
        }
    }

    private fun prepend(message: String, log: List<String>): List<String> = listOf(message) + log.take(5)
}

