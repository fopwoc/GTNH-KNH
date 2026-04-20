package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import java.util.IdentityHashMap

internal class MinecraftHostedWidgetRegistry {
    private val buttons = HostedWidgetBucket<HostedButton>()
    private val checkboxes = HostedWidgetBucket<HostedCheckbox>()
    private val selectableLists = HostedWidgetBucket<HostedSelectableList>()
    private val textFields = HostedWidgetBucket<HostedTextField>()
    private val sliders = HostedWidgetBucket<HostedSlider>()

    fun clear() {
        buttons.clear()
        checkboxes.clear()
        selectableLists.clear()
        textFields.clear()
        sliders.clear()
    }

    fun getOrCreateButton(hostKey: HostedWidgetKey, create: () -> HostedButton): HostedButton = buttons.getOrPut(hostKey, create)

    fun ownsButton(hostKey: HostedWidgetKey, hosted: HostedButton): Boolean = buttons.owns(hostKey, hosted)

    fun getOrCreateCheckbox(hostKey: HostedWidgetKey, create: () -> HostedCheckbox): HostedCheckbox = checkboxes.getOrPut(hostKey, create)

    fun ownsCheckbox(hostKey: HostedWidgetKey, hosted: HostedCheckbox): Boolean = checkboxes.owns(hostKey, hosted)

    fun getOrCreateTextField(hostKey: HostedWidgetKey, create: () -> HostedTextField): HostedTextField = textFields.getOrPut(hostKey, create)

    fun getSlider(hostKey: HostedWidgetKey): HostedSlider? = sliders[hostKey]

    fun putSlider(hostKey: HostedWidgetKey, hosted: HostedSlider) {
        sliders[hostKey] = hosted
    }

    fun ownsSlider(hostKey: HostedWidgetKey, hosted: HostedSlider): Boolean = sliders.owns(hostKey, hosted)

    fun getSelectableList(hostKey: HostedWidgetKey): HostedSelectableList? = selectableLists[hostKey]

    fun putSelectableList(hostKey: HostedWidgetKey, hosted: HostedSelectableList) {
        selectableLists[hostKey] = hosted
    }

    fun ownsSelectableList(hostKey: HostedWidgetKey, hosted: HostedSelectableList): Boolean = selectableLists.owns(hostKey, hosted)

    fun clearTextFieldFocus() {
        textFields.values().forEach { it.currentState.clearFocus() }
        updateTextFieldFocus()
    }

    fun focusTextField(target: TextFieldState) {
        textFields.values().forEach { hosted ->
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
        textFields.values().forEach { hosted ->
            hosted.widget.setFocused(hosted.currentState.focused)
        }
    }

    fun findFocusedTextField(): HostedTextField? {
        return textFields.values().firstOrNull { it.currentState.focused }
    }

    fun prune(renderEpoch: Int) {
        buttons.prune(renderEpoch)
        checkboxes.prune(renderEpoch)
        selectableLists.prune(renderEpoch)
        textFields.prune(renderEpoch) { hosted -> hosted.currentState.clearFocus() }
        sliders.prune(renderEpoch)
    }

    private class HostedWidgetBucket<V : HostedWidget> {
        // Hosted widgets are retained by opaque node identity, not value equality.
        private val widgets = IdentityHashMap<HostedWidgetKey, V>()

        operator fun get(hostKey: HostedWidgetKey): V? = widgets[hostKey]

        operator fun set(hostKey: HostedWidgetKey, hosted: V) {
            widgets[hostKey] = hosted
        }

        fun getOrPut(hostKey: HostedWidgetKey, create: () -> V): V {
            return widgets[hostKey] ?: create().also { widgets[hostKey] = it }
        }

        fun owns(hostKey: HostedWidgetKey, hosted: V): Boolean = widgets[hostKey] === hosted

        fun values(): Collection<V> = widgets.values

        fun clear() {
            widgets.clear()
        }

        fun prune(renderEpoch: Int, onRemove: (V) -> Unit = {}) {
            val iterator = widgets.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value.lastSeenEpoch != renderEpoch) {
                    onRemove(entry.value)
                    iterator.remove()
                }
            }
        }
    }
}
