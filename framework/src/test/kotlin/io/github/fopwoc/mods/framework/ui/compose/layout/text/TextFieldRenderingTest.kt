package io.github.fopwoc.mods.framework.ui.compose.layout.text

import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.minecraft.screen.TextFieldFocusManager
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.state.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TextFieldRenderingTest {
  /** Six pixels per character, no wrapping. */
  private object Metrics : TextMetrics {
    override val lineHeight: Int = 9

    override fun textWidth(text: String): Int = text.length * 6

    override fun wrapText(text: String, maxWidth: Int): List<String> = listOf(text)
  }

  @Test
  fun viewportScrollsToKeepCursorVisible() {
    val state = TextFieldState("0123456789")
    state.placeCursorAtEnd()

    val atEnd = resolveViewport(Metrics, state, availableWidth = 30)
    assertEquals(5, atEnd.scrollOffset)
    assertEquals("56789", atEnd.visibleText)

    state.scrollOffset = atEnd.scrollOffset
    state.selection = TextRange(2)
    val atStart = resolveViewport(Metrics, state, availableWidth = 30)
    assertEquals(2, atStart.scrollOffset)
    assertEquals("23456", atStart.visibleText)

    state.scrollOffset = atStart.scrollOffset
    state.selection = TextRange(4)
    assertEquals(2, resolveViewport(Metrics, state, availableWidth = 30).scrollOffset)
  }

  @Test
  fun pointerMapsToNearestCharacterBoundary() {
    val viewport = TextFieldViewport(scrollOffset = 3, visibleText = "abcd")

    assertEquals(3, indexAt(Metrics, viewport, textX = 10, pointerX = 10))
    assertEquals(4, indexAt(Metrics, viewport, textX = 10, pointerX = 15))
    assertEquals(5, indexAt(Metrics, viewport, textX = 10, pointerX = 23))
    assertEquals(7, indexAt(Metrics, viewport, textX = 10, pointerX = 999))
  }

  @Test
  fun focusManagerKeepsOneFocusedFieldAndAdoptsProgrammaticFocus() {
    val manager = TextFieldFocusManager()
    val first = TextFieldState("a")
    val second = TextFieldState("b")

    manager.focus(first)
    assertTrue(first.focused)
    manager.focus(second)
    assertFalse(first.focused)
    assertSame(second, manager.focused)

    manager.beginFrame()
    manager.rendered(first, 32)
    manager.rendered(second, 32)
    first.requestFocus()
    manager.endFrame()
    assertSame(first, manager.focused)
    assertFalse(second.focused)

    manager.beginFrame()
    manager.rendered(second, 32)
    manager.endFrame()
    assertNull(manager.focused)
    assertFalse(first.focused)
  }

  @Test
  fun defaultTrimToWidthHandlesBothDirections() {
    assertEquals("abc", Metrics.trimToWidth("abcdef", 20))
    assertEquals("def", Metrics.trimToWidth("abcdef", 20, fromEnd = true))
    assertEquals("", Metrics.trimToWidth("abc", 5))
  }
}
