package io.github.fopwoc.mods.framework.ui.compose.component

import io.github.fopwoc.mods.framework.ui.compose.component.native.resolveMultiSelection
import io.github.fopwoc.mods.framework.ui.compose.text.edit.KeyModifiers
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiSelectionTest {
  private val ctrl = KeyModifiers(ctrl = true)
  private val shift = KeyModifiers(shift = true)
  private val both = KeyModifiers(ctrl = true, shift = true)

  @Test
  fun plainClickReplacesCtrlTogglesShiftExtends() {
    assertEquals(setOf(3), resolveMultiSelection(setOf(1, 2), 3, anchor = 1, KeyModifiers.None))
    assertEquals(setOf(1, 2, 3), resolveMultiSelection(setOf(1, 2), 3, anchor = 1, ctrl))
    assertEquals(setOf(2), resolveMultiSelection(setOf(1, 2), 1, anchor = 1, ctrl))
    assertEquals(setOf(1, 2, 3, 4), resolveMultiSelection(setOf(9), 4, anchor = 1, shift))
    assertEquals(setOf(9, 1, 2), resolveMultiSelection(setOf(9), 1, anchor = 2, both))
    assertEquals(setOf(4), resolveMultiSelection(setOf(1), 4, anchor = -1, shift))
  }
}
