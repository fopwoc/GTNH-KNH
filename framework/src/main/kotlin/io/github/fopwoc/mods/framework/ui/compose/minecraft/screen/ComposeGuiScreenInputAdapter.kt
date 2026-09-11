package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputDispatcher
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.ComposeRenderRuntimeSync
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeBackDispatcher
import net.minecraft.client.Minecraft
import org.lwjgl.input.Keyboard

internal class ComposeGuiScreenInputAdapter(
    private val backDispatcher: ComposeBackDispatcher,
    private val interactionState: ComposeGuiScreenInteractionState,
    private val renderedInputTargets: List<InputTarget>,
    private val runtimeSync: ComposeRenderRuntimeSync,
    private val mouseEventReader: MouseEventReader = LwjglMouseEventReader,
    private val keyboardEnvironment: KeyboardEnvironment = LwjglKeyboardEnvironment,
) {
  fun keyTyped(
      typedChar: Char,
      keyCode: Int,
      fallback: () -> Unit,
  ) {
    runtimeSync.syncBeforeInput()
    if (
        interactionState.hasFocusedTextField &&
            interactionState.handleFocusedTextFieldKeyInput(
                typedChar,
                keyCode,
                keyboardEnvironment.modifiers(),
                keyboardEnvironment.clipboard,
            )
    ) {
      runtimeSync.syncAfterHandledInput()
      return
    }
    if (keyCode == Keyboard.KEY_ESCAPE && backDispatcher.dispatchBack()) {
      runtimeSync.syncAfterHandledInput()
      return
    }
    fallback()
  }

  fun handleMouseInput(
      width: Int,
      height: Int,
      client: Minecraft?,
      invokeBase: () -> Unit,
  ) {
    handleMouseInput(
        width = width,
        height = height,
        displayWidth = client?.displayWidth,
        displayHeight = client?.displayHeight,
        invokeBase = invokeBase,
    )
  }

  internal fun handleMouseInput(
      width: Int,
      height: Int,
      displayWidth: Int?,
      displayHeight: Int?,
      invokeBase: () -> Unit,
  ) {
    runtimeSync.syncBeforeInput()
    val rawWheelEvent = mouseEventReader.readWheelEvent()
    invokeBase()

    val resolvedEvent =
        resolveMouseWheelEvent(
            width = width,
            height = height,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            event = rawWheelEvent,
        ) ?: return

    val target =
        InputDispatcher.findTopmostWheelTarget(
            renderedInputTargets,
            resolvedEvent.mouseX,
            resolvedEvent.mouseY,
        )
    if (
        target
            ?.onWheel
            ?.invoke(resolvedEvent.mouseX, resolvedEvent.mouseY, resolvedEvent.wheelDelta) == true
    ) {
      runtimeSync.syncAfterHandledInput()
      return
    }
  }

  fun mouseClicked(
      mouseX: Int,
      mouseY: Int,
      mouseButton: Int,
      fallback: () -> Unit,
  ) {
    runtimeSync.syncBeforeInput()
    val target = InputDispatcher.findTopmostPressTarget(renderedInputTargets, mouseX, mouseY)
    val outcome = interactionState.dispatchPress(target, mouseX, mouseY, mouseButton)
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
      fallback: () -> Unit,
  ) {
    runtimeSync.syncBeforeInput()
    val outcome = interactionState.dispatchDrag(mouseX, mouseY, clickedMouseButton)
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
      fallback: () -> Unit,
  ) {
    runtimeSync.syncBeforeInput()
    if (state != -1) {
      val outcome = interactionState.dispatchRelease(mouseX, mouseY, state)
      if (outcome.handled) {
        runtimeSync.syncAfterStateMutationIf(outcome.requiresPump)
        return
      }
    }
    if (state == -1) {
      interactionState.pruneInvalidSession()
    }
    if (state != -1) {
      runtimeSync.syncAfterFallbackIfNeeded()
    }
    fallback()
  }
}
