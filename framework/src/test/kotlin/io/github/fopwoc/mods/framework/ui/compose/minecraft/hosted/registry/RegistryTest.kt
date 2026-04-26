package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted.HostedTextField
import io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted.MinecraftHostedWidgetRegistry
import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import net.minecraft.client.gui.GuiTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertFalse

class MinecraftHostedWidgetRegistryTest {
    @Test
    fun updateFocusedTextFieldCursorTicksOnlyFocusedWidget() {
        val registry = MinecraftHostedWidgetRegistry()
        val focusedState = TextFieldState("focused")
        val unfocusedState = TextFieldState("other")
        val focusedWidget = TrackingGuiTextField()
        val unfocusedWidget = TrackingGuiTextField()

        registry.getOrCreateTextField(HostedWidgetKey()) {
            HostedTextField(HostedWidgetKey(), focusedState, focusedWidget)
        }
        registry.getOrCreateTextField(HostedWidgetKey()) {
            HostedTextField(HostedWidgetKey(), unfocusedState, unfocusedWidget)
        }

        registry.focusTextField(focusedState)
        registry.updateFocusedTextFieldCursor()

        assertEquals(1, focusedWidget.cursorUpdates)
        assertEquals(0, unfocusedWidget.cursorUpdates)
    }

    @Test
    fun pruneClearsFocusForRemovedTextField() {
        val registry = MinecraftHostedWidgetRegistry()
        val focusedState = TextFieldState("focused")
        val survivingState = TextFieldState("survivor")
        val focusedHosted = registry.getOrCreateTextField(HostedWidgetKey()) {
            HostedTextField(HostedWidgetKey(), focusedState, TrackingGuiTextField())
        }
        val survivingHosted = registry.getOrCreateTextField(HostedWidgetKey()) {
            HostedTextField(HostedWidgetKey(), survivingState, TrackingGuiTextField())
        }
        focusedHosted.lastSeenEpoch = 1
        survivingHosted.lastSeenEpoch = 2
        registry.focusTextField(focusedState)

        registry.prune(renderEpoch = 2)

        assertFalse(focusedState.focused)
        assertNull(registry.findFocusedTextField())
        survivingState.requestFocus()
        survivingHosted.widget.setFocused(true)
        assertSame(survivingHosted, registry.findFocusedTextField())
    }

    private class TrackingGuiTextField : GuiTextField(null, 0, 0, 120, 20) {
        var cursorUpdates: Int = 0

        override fun updateCursorCounter() {
            cursorUpdates += 1
        }
    }
}
