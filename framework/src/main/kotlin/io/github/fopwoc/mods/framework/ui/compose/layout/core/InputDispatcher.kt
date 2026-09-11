package io.github.fopwoc.mods.framework.ui.compose.layout.core

internal object InputDispatcher {
  fun findTopmostPressTarget(targets: List<InputTarget>, mouseX: Int, mouseY: Int): InputTarget? {
    return findTopmostTarget(targets, mouseX, mouseY) { it.onPress != null }
  }

  fun findTopmostWheelTarget(targets: List<InputTarget>, mouseX: Int, mouseY: Int): InputTarget? {
    return findTopmostTarget(targets, mouseX, mouseY) { it.onWheel != null }
  }

  fun findTopmostTooltipTarget(targets: List<InputTarget>, mouseX: Int, mouseY: Int): InputTarget? {
    return findTopmostTarget(targets, mouseX, mouseY) { !it.tooltipLines.isNullOrEmpty() }
  }

  fun shouldBlurFocusedTextFieldAfterPress(
      mouseButton: Int,
      target: InputTarget?,
      pressResult: InputPressResult,
  ): Boolean {
    if (mouseButton != 0) {
      return false
    }

    return when {
      target == null -> true
      target.kind == InputTargetKind.TEXT_FIELD -> false
      else -> pressResult.consumed
    }
  }

  private inline fun findTopmostTarget(
      targets: List<InputTarget>,
      mouseX: Int,
      mouseY: Int,
      predicate: (InputTarget) -> Boolean,
  ): InputTarget? {
    for (index in targets.lastIndex downTo 0) {
      val target = targets[index]
      if (predicate(target) && target.contains(mouseX, mouseY)) {
        return target
      }
    }
    return null
  }
}
