package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputDispatcher
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.ComposeRenderRuntimeSync
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeBackDispatcher
import io.github.fopwoc.mods.framework.ui.compose.text.edit.TextClipboard

/**
 * Routes platform input into the composed tree. Every method returns true when Compose consumed the
 * input; otherwise the platform screen applies its own handling (Escape closes the screen, and so
 * on). Coordinates are GUI-scaled.
 */
internal class ComposeGuiScreenInputAdapter(
    private val backDispatcher: ComposeBackDispatcher,
    private val interactionState: ComposeGuiScreenInteractionState,
    private val renderedInputTargets: List<InputTarget>,
    private val runtimeSync: ComposeRenderRuntimeSync,
) {
    fun keyPressed(press: KeyPress, clipboard: TextClipboard): Boolean {
        runtimeSync.syncBeforeInput()
        if (interactionState.handleFocusedTextFieldKey(press, clipboard)) {
            runtimeSync.syncAfterHandledInput()
            return true
        }
        if (press.key == Key.Escape && backDispatcher.dispatchBack()) {
            runtimeSync.syncAfterHandledInput()
            return true
        }
        return false
    }

    fun charTyped(char: Char): Boolean {
        runtimeSync.syncBeforeInput()
        if (interactionState.handleFocusedTextFieldChar(char)) {
            runtimeSync.syncAfterHandledInput()
            return true
        }
        return false
    }

    /** [wheelDelta] uses LWJGL 2 units: 120 per notch, positive away from the user. */
    fun mouseScrolled(mouseX: Int, mouseY: Int, wheelDelta: Int): Boolean {
        runtimeSync.syncBeforeInput()
        if (wheelDelta == 0) return false
        val target = InputDispatcher.findTopmostWheelTarget(renderedInputTargets, mouseX, mouseY)
        if (target?.onWheel?.invoke(mouseX, mouseY, wheelDelta) == true) {
            runtimeSync.syncAfterHandledInput()
            return true
        }
        return false
    }

    fun mousePressed(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        runtimeSync.syncBeforeInput()
        val target = InputDispatcher.findTopmostPressTarget(renderedInputTargets, mouseX, mouseY)
        val outcome = interactionState.dispatchPress(target, mouseX, mouseY, mouseButton)
        if (outcome.pressResult.consumed) {
            runtimeSync.syncAfterHandledInput()
            return true
        }
        runtimeSync.syncAfterStateMutationIf(outcome.focusChanged)
        return false
    }

    fun mouseDragged(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        runtimeSync.syncBeforeInput()
        val outcome = interactionState.dispatchDrag(mouseX, mouseY, mouseButton)
        if (outcome.handled) {
            runtimeSync.syncAfterStateMutationIf(outcome.requiresPump)
            return true
        }
        return false
    }

    fun mouseReleased(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        runtimeSync.syncBeforeInput()
        val outcome = interactionState.dispatchRelease(mouseX, mouseY, mouseButton)
        if (outcome.handled) {
            runtimeSync.syncAfterStateMutationIf(outcome.requiresPump)
            return true
        }
        runtimeSync.syncAfterFallbackIfNeeded()
        return false
    }

    fun mouseMoved() {
        runtimeSync.syncBeforeInput()
        interactionState.pruneInvalidSession()
    }
}
