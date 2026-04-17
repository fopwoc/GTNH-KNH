package io.github.fopwoc.mods.framework.ui.compose

import io.github.fopwoc.mods.framework.ui.compose.layout.LayoutEngine
import io.github.fopwoc.mods.framework.ui.compose.layout.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.ButtonStyle
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutEngineTest {
    @Test
    fun centersPanelAndStretchesButtonsAcrossColumnWidth() {
        val root = LayoutElement.Box(
            modifier = Modifier().fillMaxSize(),
            children = listOf(
                LayoutElement.Column(
                    modifier = Modifier()
                        .width(200)
                        .padding(12)
                        .align(HorizontalAlignment.CENTER, VerticalAlignment.CENTER),
                    spacing = 6,
                    horizontalAlignment = HorizontalAlignment.CENTER,
                    children = listOf(
                        LayoutElement.Text(
                            text = "Title",
                            modifier = Modifier().fillMaxWidth(),
                            style = TextStyle(alignment = HorizontalAlignment.CENTER)
                        ),
                        LayoutElement.Button(
                            text = "Primary",
                            modifier = Modifier().fillMaxWidth(),
                            hostKey = Any(),
                            enabled = true,
                            style = ButtonStyle(),
                            onClick = {}
                        )
                    )
                )
            )
        )

        val layout = LayoutEngine.layout(root, FakeTextMetrics(), viewportWidth = 320, viewportHeight = 180)
        val centeredColumn = layout.children.single()
        val button = centeredColumn.children[1]

        assertEquals(200, centeredColumn.bounds.width)
        assertEquals((320 - 200) / 2, centeredColumn.bounds.x)
        assertEquals(200 - 24, button.bounds.width)
        assertTrue(button.bounds.x >= centeredColumn.bounds.x + 12)
    }

    @Test
    fun wrappedTextUsesMultipleLinesWhenWidthIsConstrained() {
        val wrappedText = LayoutElement.Text(
            text = "Wrapped text should span multiple lines in a narrow container",
            modifier = Modifier().width(72).padding(2),
            style = TextStyle(wrap = true)
        )

        val layout = LayoutEngine.layout(wrappedText, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 200)

        assertEquals(72, layout.bounds.width)
        assertTrue(layout.bounds.height > 9 + 4)
    }

    @Test
    fun scrollColumnConsumesWheelInputAndUpdatesScrollState() {
        val scrollState = ScrollState()
        val scrollColumn = LayoutElement.ScrollableColumn(
            modifier = Modifier()
                .width(140)
                .height(64)
                .padding(4),
            spacing = 4,
            horizontalAlignment = HorizontalAlignment.START,
            state = scrollState,
            children = List(6) { index ->
                LayoutElement.Button(
                    text = "Item ${index + 1}",
                    modifier = Modifier().fillMaxWidth(),
                    hostKey = Any(),
                    enabled = true,
                    style = ButtonStyle(),
                    onClick = {}
                )
            }
        )

        val layout = LayoutEngine.layout(scrollColumn, FakeTextMetrics(), viewportWidth = 140, viewportHeight = 64)
        val handled = layout.dispatchScroll(mouseX = 10, mouseY = 10, wheelDelta = -120)

        assertTrue(handled)
        assertTrue(scrollState.value > 0)
        assertTrue(scrollState.maxValue > 0)
    }

    @Test
    fun scrollColumnScrollbarCanBeDragged() {
        val scrollState = ScrollState()
        val scrollColumn = LayoutElement.ScrollableColumn(
            modifier = Modifier()
                .width(160)
                .height(72)
                .padding(4),
            spacing = 4,
            horizontalAlignment = HorizontalAlignment.START,
            state = scrollState,
            children = List(10) { index ->
                LayoutElement.Button(
                    text = "Entry ${index + 1}",
                    modifier = Modifier().fillMaxWidth(),
                    hostKey = Any(),
                    enabled = true,
                    style = ButtonStyle(),
                    onClick = {}
                )
            }
        )

        val layout = LayoutEngine.layout(scrollColumn, FakeTextMetrics(), viewportWidth = 160, viewportHeight = 72)
        val drag = layout.startScrollDrag(mouseX = 156, mouseY = 8)

        assertTrue(drag != null)
        assertTrue(drag.dragTo(48))
        assertTrue(scrollState.value > 0)
    }

    @Test
    fun checkboxConsumesClickAndTogglesValue() {
        var checked = false
        val checkbox = LayoutElement.Checkbox(
            modifier = Modifier(),
            label = "Native",
            checked = checked,
            enabled = true,
            onCheckedChange = { checked = it }
        )

        val layout = LayoutEngine.layout(checkbox, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 40)
        val handled = layout.dispatchClick(mouseX = 4, mouseY = 4)

        assertTrue(handled)
        assertEquals(true, checked)
    }

    @Test
    fun selectableListUsesVisibleRowCountForNaturalHeight() {
        val selectableList = LayoutElement.SelectableList(
            modifier = Modifier().width(140),
            hostKey = Any(),
            items = listOf("Alpha", "Beta", "Gamma", "Delta"),
            selectedIndex = 2,
            rowHeight = 18,
            visibleRowCount = 3,
            onSelectedIndexChange = {}
        )

        val layout = LayoutEngine.layout(selectableList, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 200)

        assertEquals(140, layout.bounds.width)
        assertEquals(62, layout.bounds.height)
    }

    private class FakeTextMetrics : TextMetrics {
        override val lineHeight: Int = 9

        override fun textWidth(text: String): Int = text.length * 6

        override fun wrapText(text: String, maxWidth: Int): List<String> {
            if (maxWidth <= 0) {
                return listOf(text)
            }

            return text
                .split('\n')
                .flatMap { paragraph ->
                    wrapParagraph(paragraph, maxWidth)
                }
                .ifEmpty { listOf("") }
        }

        private fun wrapParagraph(paragraph: String, maxWidth: Int): List<String> {
            if (paragraph.isEmpty()) {
                return listOf("")
            }

            val words = paragraph.split(' ')
            val lines = mutableListOf<String>()
            var currentLine = ""
            words.forEach { word ->
                val candidate = if (currentLine.isEmpty()) {
                    word
                } else {
                    "$currentLine $word"
                }
                if (textWidth(candidate) <= maxWidth || currentLine.isEmpty()) {
                    currentLine = candidate
                } else {
                    lines += currentLine
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty()) {
                lines += currentLine
            }
            return lines
        }
    }
}

