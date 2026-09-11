package io.github.fopwoc.mods.framework.ui.compose.model.alignment

import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

sealed interface VerticalArrangement {
  object Top : VerticalArrangement

  object Center : VerticalArrangement

  object Bottom : VerticalArrangement

  object SpaceBetween : VerticalArrangement

  object SpaceAround : VerticalArrangement

  object SpaceEvenly : VerticalArrangement

  data class SpacedBy(
      val space: UiUnit,
      val alignment: VerticalAlignment = VerticalAlignment.TOP,
  ) : VerticalArrangement

  companion object {
    fun spacedBy(
        space: UiUnit,
        alignment: VerticalAlignment = VerticalAlignment.TOP,
    ): VerticalArrangement = SpacedBy(space, alignment)
  }
}
