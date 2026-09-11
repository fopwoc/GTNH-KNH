package io.github.fopwoc.mods.framework.ui.compose.layout.list

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectableListRowsTest {
  @Test
  fun visibleRowsCoverTheViewportIncludingPartialRows() {
    val rows = visibleRows(itemCount = 50, rowHeight = 10, scroll = 25, viewportHeight = 40)

    assertEquals(2, rows.firstIndex)
    assertEquals(6, rows.lastIndex)
  }

  @Test
  fun visibleRowsClampToItemCount() {
    val rows = visibleRows(itemCount = 3, rowHeight = 10, scroll = 0, viewportHeight = 100)
    assertEquals(0..2, rows.indices)

    val empty = visibleRows(itemCount = 0, rowHeight = 10, scroll = 0, viewportHeight = 100)
    assertTrue(empty.indices.isEmpty())
  }

  @Test
  fun rowAtMapsPointerThroughScrollOffset() {
    val rows = visibleRows(itemCount = 10, rowHeight = 10, scroll = 15, viewportHeight = 50)

    assertEquals(1, rowAt(rows, itemCount = 10, viewportTop = 100, scroll = 15, y = 100))
    assertEquals(2, rowAt(rows, itemCount = 10, viewportTop = 100, scroll = 15, y = 105))
    assertNull(rowAt(rows, itemCount = 10, viewportTop = 100, scroll = 15, y = 190))
  }
}
