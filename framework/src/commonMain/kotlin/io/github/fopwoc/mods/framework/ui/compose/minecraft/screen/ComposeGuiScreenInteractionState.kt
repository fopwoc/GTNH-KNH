package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import io.github.fopwoc.mods.framework.ui.compose.layout.core.ActivePointerSession
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputDispatcher
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputPressResult
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.text.edit.TextClipboard

internal data class PointerDispatchOutcome(
    val handled: Boolean,
    val requiresPump: Boolean = false,
)

internal data class PressDispatchOutcome(
    val pressResult: InputPressResult,
    val focusChanged: Boolean,
)

internal class ComposeGuiScreenInteractionState(val textFields: TextFieldFocusManager) {
    private var activePointerSession: ActivePointerSession? = null

    fun reset() {
        activePointerSession = null
        textFields.reset()
    }

    val hasFocusedTextField: Boolean
        get() = textFields.focused != null

    /**
     * A focused field captures the keyboard: Escape only drops focus, other keys edit or are
     * ignored.
     */
    fun handleFocusedTextFieldKey(press: KeyPress, clipboard: TextClipboard): Boolean {
        if (textFields.focused == null) {
            return false
        }
        if (press.key == Key.Escape) {
            textFields.clearFocus()
            return true
        }
        textFields.handleKey(press.key, press.modifiers, clipboard)
        return true
    }

    fun handleFocusedTextFieldChar(char: Char): Boolean = textFields.handleChar(char)

    fun dispatchPress(
        target: InputTarget?,
        mouseX: Int,
        mouseY: Int,
        mouseButton: Int,
    ): PressDispatchOutcome {
        val pressResult =
            target?.onPress?.invoke(mouseX, mouseY, mouseButton) ?: InputPressResult.Ignored
        val focusChanged =
            InputDispatcher.shouldBlurFocusedTextFieldAfterPress(mouseButton, target, pressResult)
        if (focusChanged) {
            textFields.clearFocus()
        }
        activePointerSession =
            if (pressResult.consumed) {
                pressResult.session
            } else {
                null
            }
        return PressDispatchOutcome(
            pressResult = pressResult,
            focusChanged = focusChanged,
        )
    }

    fun dispatchDrag(
        mouseX: Int,
        mouseY: Int,
        clickedMouseButton: Int,
    ): PointerDispatchOutcome {
        val session =
            currentActivePointerSession()?.takeIf { it.button == clickedMouseButton }
                ?: return PointerDispatchOutcome(handled = false)
        return PointerDispatchOutcome(
            handled = true,
            requiresPump = session.onDrag(mouseX, mouseY),
        )
    }

    fun dispatchRelease(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): PointerDispatchOutcome {
        val session = currentActivePointerSession()?.takeIf { it.button == button }
        activePointerSession = null
        if (session == null) {
            return PointerDispatchOutcome(handled = false)
        }
        session.onRelease(mouseX, mouseY, button)
        return PointerDispatchOutcome(handled = true, requiresPump = true)
    }

    fun pruneInvalidSession() {
        currentActivePointerSession()
    }

    fun refreshAfterRender() {
        activePointerSession = activePointerSession?.takeIf(ActivePointerSession::isValid)
    }

    private fun currentActivePointerSession(): ActivePointerSession? {
        val session = activePointerSession ?: return null
        if (!session.isValid()) {
            activePointerSession = null
            return null
        }
        return session
    }
}
