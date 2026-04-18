package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import java.util.IdentityHashMap

internal class MinecraftHostedWidgetRegistry {
    val buttons = IdentityHashMap<Any, HostedButton>()
    val checkboxes = IdentityHashMap<Any, HostedCheckbox>()
    val selectableLists = IdentityHashMap<Any, HostedSelectableList>()
    val textFields = IdentityHashMap<Any, HostedTextField>()
    val sliders = IdentityHashMap<Any, HostedSlider>()

    fun clear() {
        buttons.clear()
        checkboxes.clear()
        selectableLists.clear()
        textFields.clear()
        sliders.clear()
    }

    fun clearTextFieldFocus() {
        textFields.values.forEach { it.currentState.clearFocus() }
        updateTextFieldFocus()
    }

    fun focusTextField(target: TextFieldState) {
        textFields.values.forEach { hosted ->
            val state = hosted.currentState
            val focused = state === target
            if (focused) {
                state.requestFocus()
            } else {
                state.clearFocus()
            }
            hosted.widget.setFocused(focused)
        }
    }

    fun updateTextFieldFocus() {
        textFields.values.forEach { hosted ->
            hosted.widget.setFocused(hosted.currentState.focused)
        }
    }

    fun findFocusedTextField(): HostedTextField? {
        return textFields.values.firstOrNull { it.currentState.focused }
    }

    fun prune(renderEpoch: Int) {
        pruneHostedMap<Any, HostedButton>(buttons, renderEpoch)
        pruneHostedMap<Any, HostedCheckbox>(checkboxes, renderEpoch)
        pruneHostedMap<Any, HostedSelectableList>(selectableLists, renderEpoch)
        pruneHostedMap<Any, HostedTextField>(textFields, renderEpoch) { hosted -> hosted.currentState.clearFocus() }
        pruneHostedMap<Any, HostedSlider>(sliders, renderEpoch)
    }

    private fun <K, V : HostedWidget> pruneHostedMap(
        hostedMap: IdentityHashMap<K, V>,
        renderEpoch: Int,
        onRemove: (V) -> Unit = {}
    ) {
        val iterator = hostedMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastSeenEpoch != renderEpoch) {
                onRemove(entry.value)
                iterator.remove()
            }
        }
    }
}
