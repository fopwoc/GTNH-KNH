package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import io.github.fopwoc.mods.framework.ui.compose.layout.core.ActivePointerSession
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputPressResult
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.text.edit.KeyModifiers
import io.github.fopwoc.mods.framework.ui.compose.text.edit.TextClipboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.lwjgl.input.Keyboard

class ComposeGuiScreenInteractionStateTest {
  @Test
  fun consumedNonTextFieldPressBlursFocusedTextFieldAndCapturesDragSession() {
    val textFields = TextFieldFocusManager()
    val state = TextFieldState("focused")
    textFields.focus(state)
    var dragged = false
    val interactionState = ComposeGuiScreenInteractionState(textFields)
    val target =
        InputTarget(
            kind = InputTargetKind.BUTTON,
            bounds = Rect(0, 0, 40, 20),
            onPress = { _, _, _ ->
              InputPressResult.captured(
                  ActivePointerSession(
                      button = 0,
                      onDragHandler = { _, _ ->
                        dragged = true
                        true
                      },
                  )
              )
            },
        )

    val pressOutcome =
        interactionState.dispatchPress(target, mouseX = 5, mouseY = 5, mouseButton = 0)
    val dragOutcome = interactionState.dispatchDrag(mouseX = 6, mouseY = 6, clickedMouseButton = 0)

    assertTrue(pressOutcome.pressResult.consumed)
    assertTrue(pressOutcome.focusChanged)
    assertFalse(state.focused)
    assertTrue(dragOutcome.handled)
    assertTrue(dragOutcome.requiresPump)
    assertTrue(dragged)
  }

  @Test
  fun pruneInvalidSessionDropsCapturedPointerBeforeRelease() {
    val interactionState = ComposeGuiScreenInteractionState(TextFieldFocusManager())
    var valid = true
    var released = false
    val target =
        InputTarget(
            kind = InputTargetKind.SLIDER,
            bounds = Rect(0, 0, 40, 20),
            onPress = { _, _, _ ->
              InputPressResult.captured(
                  ActivePointerSession(
                      button = 0,
                      validityCheck = { valid },
                      onReleaseHandler = { _, _, _ ->
                        released = true
                        true
                      },
                  )
              )
            },
        )

    interactionState.dispatchPress(target, mouseX = 5, mouseY = 5, mouseButton = 0)
    valid = false
    interactionState.pruneInvalidSession()
    val releaseOutcome = interactionState.dispatchRelease(mouseX = 5, mouseY = 5, button = 0)

    assertFalse(releaseOutcome.handled)
    assertFalse(released)
  }

  @Test
  fun escapeClearsFocusedTextFieldWithoutEditing() {
    val textFields = TextFieldFocusManager()
    val state = TextFieldState("focused")
    textFields.focus(state)
    val interactionState = ComposeGuiScreenInteractionState(textFields)

    val handled =
        interactionState.handleFocusedTextFieldKeyInput(
            '\u0000',
            Keyboard.KEY_ESCAPE,
            KeyModifiers.None,
            TextClipboard.None,
        )

    assertTrue(handled)
    assertFalse(state.focused)
    assertEquals("focused", state.text)
    assertFalse(interactionState.hasFocusedTextField)
  }

  @Test
  fun typedCharactersReachTheFocusedField() {
    val textFields = TextFieldFocusManager()
    val state = TextFieldState("ab")
    textFields.focus(state)
    val interactionState = ComposeGuiScreenInteractionState(textFields)

    val handled =
        interactionState.handleFocusedTextFieldKeyInput(
            'c',
            46,
            KeyModifiers.None,
            TextClipboard.None,
        )

    assertTrue(handled)
    assertEquals("abc", state.text)
  }
}
