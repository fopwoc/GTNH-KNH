package io.github.fopwoc.mods.testgui.client.gui.ui.screens.hostedstress

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class HostedStressViewModel : ViewModel() {
    val stateFlow = MutableStateFlow(HostedStressModel())

    init {
        focusField(0)
    }

    fun selectIndex(index: Int) {
        stateFlow.update { state ->
            state.copy(selectedIndex = index.coerceIn(state.items.indices))
        }
    }

    fun focusField(index: Int) {
        stateFlow.update { state ->
            val target = index.coerceIn(state.fields.indices)
            state.fields.forEachIndexed { fieldIndex, field ->
                if (fieldIndex == target) {
                    field.requestFocus()
                } else {
                    field.clearFocus()
                }
            }
            state.copy(focusedIndex = target)
        }
    }

    fun cycleFocus() {
        val state = stateFlow.value
        focusField((state.focusedIndex + 1) % state.fields.size)
    }

    fun loadSelectionIntoFocusedField() {
        stateFlow.update { state ->
            val value = state.items[state.selectedIndex]
            state.fields[state.focusedIndex].text = value
            state.copy()
        }
    }

    fun mirrorSelectionIntoAllFields() {
        stateFlow.update { state ->
            val value = state.items[state.selectedIndex]
            state.fields.forEach { field ->
                field.text = value
            }
            state.copy()
        }
    }

    fun toggleMirror(enabled: Boolean) {
        stateFlow.update { state ->
            state.copy(mirrorEnabled = enabled)
        }
    }

    fun commitSnapshot() {
        stateFlow.update { state ->
            state.copy(
                commits = state.commits + 1,
                lastSnapshot = state.fields.joinToString(separator = " | ") { field -> field.text.ifEmpty { "<empty>" } }
            )
        }
    }
}

