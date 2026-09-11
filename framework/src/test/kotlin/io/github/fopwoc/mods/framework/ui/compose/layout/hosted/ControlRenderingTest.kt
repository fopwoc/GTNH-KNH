package io.github.fopwoc.mods.framework.ui.compose.layout.hosted

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

class ControlRenderingTest {
  private fun slider(
      value: Double,
      showDecimal: Boolean = true,
      label: String = "Volume",
      suffix: String = "%",
  ) =
      LayoutElement.Slider(
          modifier = Modifier,
          value = value,
          valueRangeStart = 0.0,
          valueRangeEnd = 10.0,
          label = label,
          suffix = suffix,
          enabled = true,
          showDecimal = showDecimal,
          onValueChange = {},
      )

  @Test
  fun sliderMapsPointerToValueAndBack() {
    val bounds = Rect(10, 0, 108, 20) // 100 px of knob travel

    assertEquals(0.0, sliderValueAt(slider(0.0), bounds, pointerX = 0))
    assertEquals(5.0, sliderValueAt(slider(0.0), bounds, pointerX = 10 + 4 + 50))
    assertEquals(10.0, sliderValueAt(slider(0.0), bounds, pointerX = 500))
    assertEquals(
        7.0,
        sliderValueAt(slider(0.0, showDecimal = false), bounds, pointerX = 10 + 4 + 68),
    )
    assertEquals(0.5, sliderFraction(slider(5.0)))
  }

  @Test
  fun sliderLabelFollowsVanillaFormat() {
    assertEquals("Volume: 5.00%", sliderLabel(slider(5.0)))
    assertEquals("Volume: 5%", sliderLabel(slider(5.0, showDecimal = false)))
    assertEquals("3.00", sliderLabel(slider(3.0, label = "", suffix = "")))
  }
}
