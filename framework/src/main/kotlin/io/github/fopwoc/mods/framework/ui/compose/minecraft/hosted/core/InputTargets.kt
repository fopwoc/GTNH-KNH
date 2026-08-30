package io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted

import io.github.fopwoc.mods.framework.ui.compose.layout.core.ActivePointerSession
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputPressResult
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect

internal fun capturedPressInputTarget(
    kind: InputTargetKind,
    bounds: Rect,
    onPressAttempt: (clickX: Int, clickY: Int, button: Int) -> Boolean,
    validityCheck: () -> Boolean,
    onCaptured: () -> Unit = {},
    onRelease: (releaseX: Int, releaseY: Int, releaseButton: Int, pressedButton: Int) -> Boolean,
): InputTarget {
  return InputTarget(
      kind = kind,
      bounds = bounds,
      onPress = { clickX, clickY, button ->
        if (!onPressAttempt(clickX, clickY, button)) {
          InputPressResult.Ignored
        } else {
          onCaptured()
          InputPressResult.captured(
              ActivePointerSession(
                  button = button,
                  validityCheck = validityCheck,
                  onReleaseHandler = { releaseX, releaseY, releaseButton ->
                    onRelease(releaseX, releaseY, releaseButton, button)
                  },
              )
          )
        }
      },
  )
}
