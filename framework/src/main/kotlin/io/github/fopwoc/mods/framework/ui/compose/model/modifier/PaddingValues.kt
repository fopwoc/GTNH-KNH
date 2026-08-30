package io.github.fopwoc.mods.framework.ui.compose.model.modifier

import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved

data class PaddingValues(
    val left: UiUnit = UiUnit(0),
    val top: UiUnit = UiUnit(0),
    val right: UiUnit = UiUnit(0),
    val bottom: UiUnit = UiUnit(0),
) {
  val horizontal: UiUnit
    get() = UiUnit(left.value + right.value)

  val vertical: UiUnit
    get() = UiUnit(top.value + bottom.value)

  val horizontalValue: Int
    get() = horizontal.resolved

  val verticalValue: Int
    get() = vertical.resolved

  companion object {
    val Zero = PaddingValues()
  }
}
