package io.github.fopwoc.mods.framework.ui.compose.model.alignment

import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

sealed interface HorizontalArrangement {
  object Start : HorizontalArrangement

  object Center : HorizontalArrangement

  object End : HorizontalArrangement

  object SpaceBetween : HorizontalArrangement

  object SpaceAround : HorizontalArrangement

  object SpaceEvenly : HorizontalArrangement

  data class SpacedBy(
      val space: UiUnit,
      val alignment: HorizontalAlignment = HorizontalAlignment.START,
  ) : HorizontalArrangement

  companion object {
    fun spacedBy(
        space: UiUnit,
        alignment: HorizontalAlignment = HorizontalAlignment.START,
    ): HorizontalArrangement = SpacedBy(space, alignment)
  }
}
