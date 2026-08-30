package io.github.fopwoc.mods.framework.ui.compose

import io.github.fopwoc.mods.framework.ui.compose.layout.core.ActivePointerSession
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputDispatcher
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputPressResult
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InputDispatchTest {
  @Test
  fun laterRegisteredOverlappingPressTargetWins() {
    val expected =
        InputTarget(
            kind = InputTargetKind.BUTTON,
            bounds = Rect(0, 0, 40, 20),
            onPress = { _, _, _ -> InputPressResult.Consumed },
        )
    val targets =
        listOf(
            InputTarget(
                kind = InputTargetKind.BUTTON,
                bounds = Rect(0, 0, 40, 20),
                onPress = { _, _, _ -> InputPressResult.Consumed },
            ),
            expected,
        )

    val actual = InputDispatcher.findTopmostPressTarget(targets, mouseX = 10, mouseY = 10)

    assertSame(expected, actual)
  }

  @Test
  fun clippedTargetIsIgnoredByHitTesting() {
    val unclipped =
        InputTarget(
            kind = InputTargetKind.BUTTON,
            bounds = Rect(0, 0, 40, 20),
            onPress = { _, _, _ -> InputPressResult.Consumed },
        )
    val clippedOut =
        InputTarget(
            kind = InputTargetKind.SLIDER,
            bounds = Rect(0, 0, 40, 20),
            clipRect = Rect(20, 0, 20, 20),
            onPress = { _, _, _ -> InputPressResult.Consumed },
        )

    val actual =
        InputDispatcher.findTopmostPressTarget(
            listOf(unclipped, clippedOut),
            mouseX = 10,
            mouseY = 10,
        )

    assertSame(unclipped, actual)
  }

  @Test
  fun returnsNullWhenNoInteractiveTargetContainsPoint() {
    val targets =
        listOf(
            InputTarget(kind = InputTargetKind.BUTTON, bounds = Rect(0, 0, 10, 10)),
            InputTarget(kind = InputTargetKind.SCROLL_WHEEL, bounds = Rect(20, 20, 10, 10)),
        )

    val pressTarget = InputDispatcher.findTopmostPressTarget(targets, mouseX = 15, mouseY = 15)
    val wheelTarget = InputDispatcher.findTopmostWheelTarget(targets, mouseX = 15, mouseY = 15)

    assertNull(pressTarget)
    assertNull(wheelTarget)
  }

  @Test
  fun wheelDispatchPrefersTopmostRegisteredTarget() {
    val calls = mutableListOf<String>()
    val targets =
        listOf(
            InputTarget(
                kind = InputTargetKind.SCROLL_WHEEL,
                bounds = Rect(0, 0, 60, 60),
                onWheel = { _, _, _ ->
                  calls += "bottom"
                  true
                },
            ),
            InputTarget(
                kind = InputTargetKind.SELECTABLE_LIST,
                bounds = Rect(0, 0, 60, 60),
                onWheel = { _, _, _ ->
                  calls += "top"
                  true
                },
            ),
        )

    val target = InputDispatcher.findTopmostWheelTarget(targets, mouseX = 12, mouseY = 12)
    val handled = target?.onWheel?.invoke(12, 12, -120) == true

    assertTrue(handled)
    assertEquals(listOf("top"), calls)
  }

  @Test
  fun laterRegisteredTooltipTargetWins() {
    val expected =
        InputTarget(
            kind = InputTargetKind.TOOLTIP,
            bounds = Rect(0, 0, 40, 20),
            tooltipLines = listOf("top"),
        )
    val actual =
        InputDispatcher.findTopmostTooltipTarget(
            targets =
                listOf(
                    InputTarget(
                        kind = InputTargetKind.TOOLTIP,
                        bounds = Rect(0, 0, 40, 20),
                        tooltipLines = listOf("bottom"),
                    ),
                    expected,
                ),
            mouseX = 10,
            mouseY = 10,
        )

    assertSame(expected, actual)
  }

  @Test
  fun clippedTooltipTargetIsIgnoredByHitTesting() {
    val unclipped =
        InputTarget(
            kind = InputTargetKind.TOOLTIP,
            bounds = Rect(0, 0, 40, 20),
            tooltipLines = listOf("visible"),
        )
    val clippedOut =
        InputTarget(
            kind = InputTargetKind.TOOLTIP,
            bounds = Rect(0, 0, 40, 20),
            clipRect = Rect(20, 0, 20, 20),
            tooltipLines = listOf("hidden"),
        )

    val actual =
        InputDispatcher.findTopmostTooltipTarget(
            listOf(unclipped, clippedOut),
            mouseX = 10,
            mouseY = 10,
        )

    assertSame(unclipped, actual)
  }

  @Test
  fun textFieldPressKeepsFocus() {
    val target =
        InputTarget(
            kind = InputTargetKind.TEXT_FIELD,
            bounds = Rect(0, 0, 80, 20),
            onPress = { _, _, _ -> InputPressResult.Consumed },
        )

    val shouldBlur =
        InputDispatcher.shouldBlurFocusedTextFieldAfterPress(
            mouseButton = 0,
            target = target,
            pressResult = InputPressResult.Consumed,
        )

    assertTrue(!shouldBlur)
  }

  @Test
  fun consumedNonTextFieldPressClearsFocus() {
    val target =
        InputTarget(
            kind = InputTargetKind.BUTTON,
            bounds = Rect(0, 0, 80, 20),
            onPress = { _, _, _ -> InputPressResult.Consumed },
        )

    val shouldBlur =
        InputDispatcher.shouldBlurFocusedTextFieldAfterPress(
            mouseButton = 0,
            target = target,
            pressResult = InputPressResult.Consumed,
        )

    assertTrue(shouldBlur)
  }

  @Test
  fun backgroundLeftClickClearsFocus() {
    val shouldBlur =
        InputDispatcher.shouldBlurFocusedTextFieldAfterPress(
            mouseButton = 0,
            target = null,
            pressResult = InputPressResult.Ignored,
        )

    assertTrue(shouldBlur)
  }

  @Test
  fun ignoredNonTextFieldPressKeepsFocus() {
    val target =
        InputTarget(
            kind = InputTargetKind.BUTTON,
            bounds = Rect(0, 0, 80, 20),
            onPress = { _, _, _ -> InputPressResult.Ignored },
        )

    val shouldBlur =
        InputDispatcher.shouldBlurFocusedTextFieldAfterPress(
            mouseButton = 0,
            target = target,
            pressResult = InputPressResult.Ignored,
        )

    assertTrue(!shouldBlur)
  }

  @Test
  fun pressCanCaptureDragAndReleaseSession() {
    val events = mutableListOf<String>()
    val session =
        ActivePointerSession(
            button = 0,
            onDragHandler = { mouseX, mouseY ->
              events += "drag:$mouseX,$mouseY"
              true
            },
            onReleaseHandler = { mouseX, mouseY, button ->
              events += "release:$mouseX,$mouseY,$button"
              true
            },
        )
    val target =
        InputTarget(
            kind = InputTargetKind.SLIDER,
            bounds = Rect(0, 0, 100, 20),
            onPress = { _, _, button ->
              if (button == 0) {
                InputPressResult.captured(session)
              } else {
                InputPressResult.Ignored
              }
            },
        )

    val pressResult = target.onPress?.invoke(10, 10, 0)
    val capturedSession = pressResult?.session

    assertTrue(pressResult?.consumed == true)
    assertNotNull(capturedSession)
    assertTrue(capturedSession.onDrag(42, 11))
    assertTrue(capturedSession.onRelease(55, 12, 0))
    assertEquals(listOf("drag:42,11", "release:55,12,0"), events)
  }
}
