package io.github.fopwoc.mods.framework.ui.compose.layout.core

internal enum class InputTargetKind {
  BUTTON,
  CHECKBOX,
  SLIDER,
  SELECTABLE_LIST,
  TEXT_FIELD,
  SCROLL_WHEEL,
  SCROLL_THUMB,
  TOOLTIP,
  CLICKABLE,
}

internal data class InputTarget(
    val kind: InputTargetKind,
    val bounds: Rect,
    val clipRect: Rect? = null,
    val onPress: ((mouseX: Int, mouseY: Int, button: Int) -> InputPressResult)? = null,
    val onWheel: ((mouseX: Int, mouseY: Int, wheelDelta: Int) -> Boolean)? = null,
    val tooltipLines: List<String>? = null,
) {
  fun contains(mouseX: Int, mouseY: Int): Boolean {
    return bounds.contains(mouseX, mouseY) && clipRect?.contains(mouseX, mouseY) != false
  }
}
