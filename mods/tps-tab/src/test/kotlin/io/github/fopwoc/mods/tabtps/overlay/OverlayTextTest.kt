package io.github.fopwoc.mods.tabtps.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OverlayTextTest {
  @Test
  fun leavesTextThatFitsUnchanged() {
    assertEquals("Overworld #0", ellipsize("Overworld #0", 20))
  }

  @Test
  fun ellipsizesLongLabelsWithinTheirColumn() {
    val result = ellipsize("Current · Extremely Long Dimension Name #42", 18)

    assertEquals("Current · Extreme…", result)
    assertTrue(result.length <= 18)
  }

  private fun ellipsize(text: String, maxWidth: Int): String =
      OverlayText.ellipsize(
          text = text,
          maxWidth = maxWidth,
          widthOf = String::length,
          trimToWidth = { value, width -> value.take(width) },
      )
}
