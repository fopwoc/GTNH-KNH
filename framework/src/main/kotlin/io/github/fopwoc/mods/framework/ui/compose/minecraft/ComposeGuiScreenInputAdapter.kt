package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.layout.InputDispatcher
import net.minecraft.client.Minecraft
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

internal class ComposeGuiScreenInputAdapter(
    private val context: ComposeGuiScreenContext,
    private val runtimeSync: ComposeGuiScreenRuntimeSync
) {
    fun keyTyped(
        typedChar: Char,
        keyCode: Int,
        fallback: () -> Unit
    ) {
        runtimeSync.syncBeforeInput()
        if (context.interactionState.handleFocusedTextFieldKeyInput(typedChar, keyCode)) {
            runtimeSync.syncAfterHandledInput()
            return
        }
        if (keyCode == Keyboard.KEY_ESCAPE && context.backDispatcher.dispatchBack()) {
            runtimeSync.syncAfterHandledInput()
            return
        }
        fallback()
    }

    fun handleMouseInput(
        width: Int,
        height: Int,
        client: Minecraft?,
        invokeBase: () -> Unit
    ) {
        runtimeSync.syncBeforeInput()
        invokeBase()

        val wheelDelta = Mouse.getEventDWheel()
        if (wheelDelta == 0 || width <= 0 || height <= 0 || client == null) {
            return
        }

        val mouseX = Mouse.getEventX() * width / client.displayWidth
        val mouseY = height - Mouse.getEventY() * height / client.displayHeight - 1
        val target = InputDispatcher.findTopmostWheelTarget(context.renderedInputTargets, mouseX, mouseY)
        if (target?.onWheel?.invoke(mouseX, mouseY, wheelDelta) == true) {
            runtimeSync.syncAfterHandledInput()
        }
    }

    fun mouseClicked(
        mouseX: Int,
        mouseY: Int,
        mouseButton: Int,
        fallback: () -> Unit
    ) {
        runtimeSync.syncBeforeInput()
        val target = InputDispatcher.findTopmostPressTarget(context.renderedInputTargets, mouseX, mouseY)
        val outcome = context.interactionState.dispatchPress(target, mouseX, mouseY, mouseButton)
        if (outcome.pressResult.consumed) {
            runtimeSync.syncAfterHandledInput()
            return
        }

        runtimeSync.syncAfterStateMutationIf(outcome.focusChanged)
        fallback()
    }

    fun mouseClickMove(
        mouseX: Int,
        mouseY: Int,
        clickedMouseButton: Int,
        fallback: () -> Unit
    ) {
        runtimeSync.syncBeforeInput()
        val outcome = context.interactionState.dispatchDrag(mouseX, mouseY, clickedMouseButton)
        if (outcome.handled) {
            runtimeSync.syncAfterStateMutationIf(outcome.requiresPump)
            return
        }
        fallback()
    }

    fun mouseMovedOrUp(
        mouseX: Int,
        mouseY: Int,
        state: Int,
        fallback: () -> Unit
    ) {
        runtimeSync.syncBeforeInput()
        if (state != -1) {
            val outcome = context.interactionState.dispatchRelease(mouseX, mouseY, state)
            if (outcome.handled) {
                runtimeSync.syncAfterStateMutationIf(outcome.requiresPump)
                return
            }
        }
        if (state == -1) {
            context.interactionState.pruneInvalidSession()
        }
        if (state != -1) {
            runtimeSync.syncAfterFallbackIfNeeded()
        }
        fallback()
    }
}
