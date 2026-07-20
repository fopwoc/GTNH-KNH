package io.github.fopwoc.mods.testgui.client.gui.ui.screens.inputs

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class InputsViewModel : ViewModel() {
    val stateFlow = MutableStateFlow(InputsModel())

    fun selectIndex(index: Int) {
        stateFlow.update { state ->
            state.copy(selectedIndex = index.coerceIn(state.items.indices))
        }
    }

    fun loadSelectionIntoField() {
        stateFlow.update { state ->
            val selectedText = state.items[state.selectedIndex]
            state.fieldState.text = selectedText
            state.fieldState.requestFocus()
            state.copy(loadCount = state.loadCount + 1)
        }
    }

    fun commitDraft() {
        stateFlow.update { state ->
            state.copy(
                lastCommittedText = state.fieldState.text,
                commitCount = state.commitCount + 1
            )
        }
    }

    fun requestFocus() {
        stateFlow.value.fieldState.requestFocus()
    }

    fun clearFocus() {
        stateFlow.value.fieldState.clearFocus()
    }

    fun resetDraft() {
        stateFlow.update { state ->
            state.fieldState.text = ""
            state.copy()
        }
    }
}

