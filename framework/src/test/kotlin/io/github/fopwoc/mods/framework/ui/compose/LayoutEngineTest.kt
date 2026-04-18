package io.github.fopwoc.mods.framework.ui.compose

import io.github.fopwoc.mods.framework.ui.compose.layout.LayoutEngine
import io.github.fopwoc.mods.framework.ui.compose.layout.InputDispatcher
import io.github.fopwoc.mods.framework.ui.compose.layout.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
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
                    onClick = {}
                )
            }
        )

        val layout = LayoutEngine.layout(scrollColumn, FakeTextMetrics(), viewportWidth = 140, viewportHeight = 64)
        val context = RecordingRenderContext(viewportWidth = 140, viewportHeight = 64)
        layout.draw(context)
        val wheelTarget = InputDispatcher.findTopmostWheelTarget(context.inputTargets, mouseX = 10, mouseY = 10)
        val handled = wheelTarget?.onWheel?.invoke(10, 10, -120) == true

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
                    onClick = {}
                )
            }
        )

        val layout = LayoutEngine.layout(scrollColumn, FakeTextMetrics(), viewportWidth = 160, viewportHeight = 72)
        val context = RecordingRenderContext(viewportWidth = 160, viewportHeight = 72)
        layout.draw(context)
        val pressTarget = InputDispatcher.findTopmostPressTarget(context.inputTargets, mouseX = 151, mouseY = 8)
        val pressResult = pressTarget?.onPress?.invoke(151, 8, 0)
        val drag = pressResult?.session

        assertTrue(pressResult?.consumed == true)
        assertTrue(drag != null)
        assertTrue(drag.onDrag(156, 48))
        assertTrue(scrollState.value > 0)
    }

    @Test
    fun checkboxKeepsNaturalSizeForHostedRendering() {
        val checkbox = LayoutElement.Checkbox(
            modifier = Modifier(),
            hostKey = Any(),
            label = "Native",
            checked = false,
            enabled = true,
            onCheckedChange = {}
        )

        val layout = LayoutEngine.layout(checkbox, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 40)

        assertTrue(layout.bounds.width >= 13 + ("Native".length * 6))
        assertTrue(layout.bounds.height >= 11)
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

    private class RecordingRenderContext(
        override val viewportWidth: Int,
        override val viewportHeight: Int
    ) : RenderContext {
        override val mouseX: Int = 0
        override val mouseY: Int = 0
        override val lineHeight: Int = 9

        val inputTargets = mutableListOf<InputTarget>()

        private var activeClipRect: Rect? = null

        override fun textWidth(text: String): Int = text.length * 6

        override fun wrapText(text: String, maxWidth: Int): List<String> {
            return FakeTextMetrics().wrapText(text, maxWidth)
        }

        override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) = Unit

        override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Int) = Unit

        override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Int) = Unit

        override fun drawText(text: String, x: Int, y: Int, color: Int, shadow: Boolean) = Unit

        override fun drawVanillaButton(
            bounds: Rect,
            hostKey: Any,
            text: String,
            enabled: Boolean,
            onClick: () -> Unit
        ) = Unit

        override fun drawVanillaCheckbox(
            bounds: Rect,
            hostKey: Any,
            label: String,
            checked: Boolean,
            enabled: Boolean,
            onCheckedChange: (Boolean) -> Unit
        ) = Unit

        override fun drawVanillaTextField(
            bounds: Rect,
            hostKey: Any,
            state: io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState,
            placeholder: String,
            enabled: Boolean,
            style: io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
        ) = Unit

        override fun drawVanillaSlider(
            bounds: Rect,
            hostKey: Any,
            value: Double,
            valueRangeStart: Double,
            valueRangeEnd: Double,
            label: String,
            suffix: String,
            enabled: Boolean,
            showDecimal: Boolean,
            onValueChange: (Double) -> Unit
        ) = Unit

        override fun drawVanillaSelectableList(
            bounds: Rect,
            hostKey: Any,
            items: List<String>,
            selectedIndex: Int,
            rowHeight: Int,
            onSelectedIndexChange: (Int) -> Unit
        ) = Unit

        override fun registerInputTarget(target: InputTarget) {
            val combinedClipRect = when {
                activeClipRect == null -> target.clipRect
                target.clipRect == null -> activeClipRect
                else -> activeClipRect!!.intersect(target.clipRect)
            }
            inputTargets += target.copy(clipRect = combinedClipRect)
        }

        override fun withClipRect(rect: Rect, block: () -> Unit) {
            val previousClipRect = activeClipRect
            activeClipRect = previousClipRect?.intersect(rect) ?: rect
            try {
                block()
            } finally {
                activeClipRect = previousClipRect
            }
        }
    }
}

