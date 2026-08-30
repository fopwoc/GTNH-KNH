package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import io.github.fopwoc.mods.framework.ui.compose.layout.core.ActivePointerSession
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputPressResult
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted.HostedTextField
import io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted.MinecraftHostedWidgetRegistry
import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.minecraft.client.gui.GuiTextField
import org.lwjgl.input.Keyboard

class ComposeGuiScreenInteractionStateTest {
  @Test
  fun consumedNonTextFieldPressBlursFocusedTextFieldAndCapturesDragSession() {
    val registry = MinecraftHostedWidgetRegistry()
    val state = TextFieldState("focused")
    val hostKey = HostedWidgetKey()
    val hosted =
        registry.getOrCreateTextField(hostKey) {
          HostedTextField(hostKey, state, GuiTextField(null, 0, 0, 120, 20))
        }
    registry.focusTextField(state)
    var dragged = false
    val interactionState = ComposeGuiScreenInteractionState(registry)
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
    assertFalse(hosted.widget.isFocused)
    assertTrue(dragOutcome.handled)
    assertTrue(dragOutcome.requiresPump)
    assertTrue(dragged)
  }

  @Test
  fun pruneInvalidSessionDropsCapturedPointerBeforeRelease() {
    val interactionState = ComposeGuiScreenInteractionState(MinecraftHostedWidgetRegistry())
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
  fun escapeClearsFocusedTextFieldWithoutFallingThroughToWidgetEditing() {
    val registry = MinecraftHostedWidgetRegistry()
    val state = TextFieldState("focused")
    val hostKey = HostedWidgetKey()
    val hosted =
        registry.getOrCreateTextField(hostKey) {
          HostedTextField(hostKey, state, GuiTextField(null, 0, 0, 120, 20))
        }
    registry.focusTextField(state)
    val interactionState = ComposeGuiScreenInteractionState(registry)

    val handled = interactionState.handleFocusedTextFieldKeyInput('\u0000', Keyboard.KEY_ESCAPE)

    assertTrue(handled)
    assertFalse(state.focused)
    assertFalse(hosted.widget.isFocused)
  }
}
