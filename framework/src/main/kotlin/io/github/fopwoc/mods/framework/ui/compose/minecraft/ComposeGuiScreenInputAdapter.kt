package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.layout.InputDispatcher
import net.minecraft.client.Minecraft
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

internal class ComposeGuiScreenInputAdapter(
    private val context: ComposeGuiScreenContext
) {
    fun keyTyped(
        typedChar: Char,
        keyCode: Int,
        fallback: () -> Unit
    ) {
        context.runtime.pump()
        if (context.interactionState.handleFocusedTextFieldKeyInput(typedChar, keyCode)) {
            context.runtime.pump()
            return
        }
        if (keyCode == Keyboard.KEY_ESCAPE && context.backDispatcher.dispatchBack()) {
            context.runtime.pump()
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
        context.runtime.pump()
        invokeBase()

        val wheelDelta = Mouse.getEventDWheel()
        if (wheelDelta == 0 || width <= 0 || height <= 0 || client == null) {
            return
        }

        val mouseX = Mouse.getEventX() * width / client.displayWidth
        val mouseY = height - Mouse.getEventY() * height / client.displayHeight - 1
        val target = InputDispatcher.findTopmostWheelTarget(context.renderedInputTargets, mouseX, mouseY)
        if (target?.onWheel?.invoke(mouseX, mouseY, wheelDelta) == true) {
            context.runtime.pump()
        }
    }

    fun mouseClicked(
        mouseX: Int,
        mouseY: Int,
        mouseButton: Int,
        fallback: () -> Unit
    ) {
        context.runtime.pump()
        val target = InputDispatcher.findTopmostPressTarget(context.renderedInputTargets, mouseX, mouseY)
        val outcome = context.interactionState.dispatchPress(target, mouseX, mouseY, mouseButton)
        if (outcome.pressResult.consumed) {
            context.runtime.pump()
            return
        }

        if (outcome.focusChanged) {
            context.runtime.pump()
        }
        fallback()
    }

    fun mouseClickMove(
        mouseX: Int,
        mouseY: Int,
        clickedMouseButton: Int,
        fallback: () -> Unit
    ) {
        context.runtime.pump()
        val outcome = context.interactionState.dispatchDrag(mouseX, mouseY, clickedMouseButton)
        if (outcome.handled) {
            if (outcome.requiresPump) {
                context.runtime.pump()
            }
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
        context.runtime.pump()
        if (state != -1) {
            val outcome = context.interactionState.dispatchRelease(mouseX, mouseY, state)
            if (outcome.handled) {
                if (outcome.requiresPump) {
                    context.runtime.pump()
                }
                return
            }
        }
        if (state == -1) {
            context.interactionState.pruneInvalidSession()
        }
        if (state != -1 && context.runtime.hasPendingNotifications) {
            context.runtime.pump()
        }
        fallback()
    }
}
