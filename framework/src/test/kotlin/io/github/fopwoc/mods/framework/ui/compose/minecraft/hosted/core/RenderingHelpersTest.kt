package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted.capturedPressInputTarget
import io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted.shouldCaptureHostedButtonPress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MinecraftHostedWidgetRenderingTest {

  @Test
  fun hostedButtonPressHelperOnlyAcceptsEnabledPrimaryClicksInsideBounds() {
    val bounds = Rect(10, 20, 80, 20)

    assertTrue(
        shouldCaptureHostedButtonPress(
            bounds = bounds,
            enabled = true,
            clickX = 10,
            clickY = 20,
            button = 0,
        )
    )
    assertFalse(
        shouldCaptureHostedButtonPress(
            bounds = bounds,
            enabled = true,
            clickX = 9,
            clickY = 20,
            button = 0,
        )
    )
    assertFalse(
        shouldCaptureHostedButtonPress(
            bounds = bounds,
            enabled = false,
            clickX = 15,
            clickY = 25,
            button = 0,
        )
    )
    assertFalse(
        shouldCaptureHostedButtonPress(
            bounds = bounds,
            enabled = true,
            clickX = 15,
            clickY = 25,
            button = 1,
        )
    )
  }

  @Test
  fun capturedPressInputTargetCreatesOwnedPointerSession() {
    var captured = 0
    var released = 0
    var valid = true
    val target =
        capturedPressInputTarget(
            kind = InputTargetKind.BUTTON,
            bounds = Rect(0, 0, 80, 20),
            onPressAttempt = { _, _, button -> button == 0 },
            validityCheck = { valid },
            onCaptured = { captured += 1 },
            onRelease = { _, _, releaseButton, pressedButton ->
              released += 1
              releaseButton == pressedButton
            },
        )

    val pressResult = target.onPress?.invoke(10, 10, 0)
    val session = pressResult?.session

    assertTrue(pressResult?.consumed == true)
    assertEquals(1, captured)
    assertNotNull(session)
    assertTrue(session.isValid())
    assertTrue(session.onRelease(11, 12, 0))
    assertEquals(1, released)

    valid = false
    assertFalse(session.isValid())
  }

  @Test
  fun capturedPressInputTargetReturnsIgnoredWhenPressIsRejected() {
    var captured = 0
    val target =
        capturedPressInputTarget(
            kind = InputTargetKind.CHECKBOX,
            bounds = Rect(0, 0, 80, 20),
            onPressAttempt = { _, _, _ -> false },
            validityCheck = { true },
            onCaptured = { captured += 1 },
            onRelease = { _, _, _, _ -> true },
        )

    val pressResult = target.onPress?.invoke(10, 10, 1)

    assertTrue(pressResult?.consumed == false)
    assertNull(pressResult.session)
    assertEquals(0, captured)
  }
}
