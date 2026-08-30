package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import io.github.fopwoc.mods.framework.ui.compose.layout.core.ActivePointerSession
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputDispatcher
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputPressResult
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted.MinecraftHostedWidgetRegistry
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import org.lwjgl.input.Keyboard

internal data class PointerDispatchOutcome(
    val handled: Boolean,
    val requiresPump: Boolean = false,
)

internal data class PressDispatchOutcome(
    val pressResult: InputPressResult,
    val focusChanged: Boolean,
)

internal class ComposeGuiScreenInteractionState(
    private val hostedWidgets: MinecraftHostedWidgetRegistry
) {
  private var activePointerSession: ActivePointerSession? = null

  fun reset() {
    activePointerSession = null
  }

  fun focusTextField(target: TextFieldState) {
    hostedWidgets.focusTextField(target)
  }

  fun handleFocusedTextFieldKeyInput(typedChar: Char, keyCode: Int): Boolean {
    val focusedHosted = hostedWidgets.findFocusedTextField() ?: return false
    val state = focusedHosted.currentState
    if (keyCode == Keyboard.KEY_ESCAPE) {
      state.clearFocus()
      focusedHosted.widget.setFocused(false)
      return true
    }

    val handled = focusedHosted.widget.textboxKeyTyped(typedChar, keyCode)
    if (handled) {
      state.text = focusedHosted.widget.text
      state.syncFocus(focusedHosted.widget.isFocused)
    }
    return handled
  }

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
      hostedWidgets.clearTextFieldFocus()
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
