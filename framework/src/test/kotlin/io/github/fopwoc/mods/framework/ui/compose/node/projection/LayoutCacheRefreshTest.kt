package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.ComposeRenderLayoutState
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.render.HostedElementRenderer
import io.github.fopwoc.mods.framework.ui.compose.render.NoOpHostedElementRenderer
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LayoutCacheRefreshTest {
    @Test
    fun hostedButtonEqualityIgnoresCallbackIdentity() {
        val first = LayoutElement.Button(
            modifier = Modifier.fillMaxWidth(),
            hostKey = HostedWidgetKey(),
            text = StyledText.of("Apply"),
            enabled = true,
            onClick = {}
        )
        val second = LayoutElement.Button(
            modifier = Modifier.fillMaxWidth(),
            hostKey = first.hostKey,
            text = StyledText.of("Apply"),
            enabled = true,
            onClick = { error("different callback instance") }
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun callbackOnlyRecompositionRefreshesHostedBindingsWithoutRebuildingLayout() {
        val layoutState = ComposeRenderLayoutState()
        val root = RootNode()
        val buttonNode = ButtonNode(
            modifier = Modifier.fillMaxWidth(),
            text = StyledText.of("Run"),
            enabled = true,
            onClick = {}
        )
        root.children += buttonNode

        val firstClicks = mutableListOf<String>()
        buttonNode.onClick = { firstClicks += "first" }

        val renderContext = RecordingRenderContext()
        val firstLayout = layoutState.ensureLayout(root, renderContext, width = 220, height = 80)
        val firstHostedRenderer = RecordingHostedElementRenderer()
        firstLayout.draw(renderContext, firstHostedRenderer)
        firstHostedRenderer.lastButtonClick?.invoke()
        assertEquals(listOf("first"), firstClicks)

        val secondClicks = mutableListOf<String>()
        buttonNode.onClick = { secondClicks += "second" }
        layoutState.invalidateComposition()

        val refreshedContext = RecordingRenderContext()
        val secondLayout = layoutState.ensureLayout(root, refreshedContext, width = 220, height = 80)
        val secondHostedRenderer = RecordingHostedElementRenderer()
        secondLayout.draw(refreshedContext, secondHostedRenderer)
        secondHostedRenderer.lastButtonClick?.invoke()

        assertSame(firstLayout, secondLayout)
        assertEquals(listOf("second"), secondClicks)
    }

    @Test
    fun selectableListStateOnlyRecompositionRefreshesHostedBindingsWithoutRebuildingLayout() {
        val layoutState = ComposeRenderLayoutState()
        val root = RootNode()
        val listNode = SelectableListNode(
            modifier = Modifier.width(140.uu),
            items = listOf("Alpha", "Beta", "Gamma"),
            selectedIndex = 0,
            rowHeight = 18.uu,
            visibleRowCount = 2,
            onSelectedIndexChange = {}
        )
        root.children += listNode

        val firstSelections = mutableListOf<Int>()
        listNode.onSelectedIndexChange = { firstSelections += it }

        val renderContext = RecordingRenderContext()
        val firstLayout = layoutState.ensureLayout(root, renderContext, width = 220, height = 120)
        val firstHostedRenderer = RecordingHostedElementRenderer()
        firstLayout.draw(renderContext, firstHostedRenderer)
        firstHostedRenderer.lastSelectableListSelectionChange?.invoke(2)

        assertEquals(0, firstHostedRenderer.lastSelectableListSelectedIndex)
        assertEquals(listOf(2), firstSelections)

        val secondSelections = mutableListOf<Int>()
        listNode.selectedIndex = 1
        listNode.onSelectedIndexChange = { secondSelections += it }
        layoutState.invalidateComposition()

        val refreshedContext = RecordingRenderContext()
        val secondLayout = layoutState.ensureLayout(root, refreshedContext, width = 220, height = 120)
        val secondHostedRenderer = RecordingHostedElementRenderer()
        secondLayout.draw(refreshedContext, secondHostedRenderer)
        secondHostedRenderer.lastSelectableListSelectionChange?.invoke(0)

        assertSame(firstLayout, secondLayout)
        assertEquals(1, secondHostedRenderer.lastSelectableListSelectedIndex)
        assertEquals(listOf(0), secondSelections)
    }

    @Test
    fun scrollStateChangesRefreshPlacementWithoutRebuildingLayout() {
        val scrollState = ScrollState()
        val layoutState = ComposeRenderLayoutState()
        val root = RootNode()
        val scrollableColumn = ColumnNode(
            modifier = Modifier.fillMaxWidth().height(40.uu).verticalScroll(scrollState),
            verticalArrangement = VerticalArrangement.spacedBy(0.uu),
            horizontalAlignment = HorizontalAlignment.START
        )
        scrollableColumn.children += SpacerNode(modifier = Modifier.fillMaxWidth().height(30.uu))
        scrollableColumn.children += SpacerNode(modifier = Modifier.fillMaxWidth().height(30.uu))
        root.children += scrollableColumn

        val renderContext = RecordingRenderContext()
        val firstLayout = layoutState.ensureLayout(root, renderContext, width = 120, height = 40)
        val firstChildY = firstLayout.children.single().children.first().bounds.y
        assertTrue(scrollState.maxValue > 0)

        scrollState.scrollTo(10)
        layoutState.invalidateComposition()

        val secondLayout = layoutState.ensureLayout(root, renderContext, width = 120, height = 40)
        val secondChildY = secondLayout.children.single().children.first().bounds.y

        assertSame(firstLayout, secondLayout)
        assertNotEquals(firstChildY, secondChildY)
    }

    @Test
    fun changingColumnBetweenScrollableAndNonScrollableForcesRelayout() {
        val layoutState = ComposeRenderLayoutState()
        val root = RootNode()
        val columnNode = ColumnNode(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = VerticalArrangement.spacedBy(0.uu),
            horizontalAlignment = HorizontalAlignment.START
        )
        columnNode.children += SpacerNode(modifier = Modifier.fillMaxWidth().height(20.uu))
        root.children += columnNode

        val renderContext = RecordingRenderContext()
        val firstLayout = layoutState.ensureLayout(root, renderContext, width = 120, height = 40)

        columnNode.modifier = columnNode.modifier.verticalScroll(ScrollState())
        layoutState.invalidateComposition()

        val secondLayout = layoutState.ensureLayout(root, renderContext, width = 120, height = 40)

        assertNotSame(firstLayout, secondLayout)
        assertEquals(LayoutElement.Column::class, firstLayout.children.single().element::class)
        assertEquals(LayoutElement.ScrollableColumn::class, secondLayout.children.single().element::class)
    }

    private class RecordingRenderContext : RenderContext {
        override val viewportWidth: Int = 320
        override val viewportHeight: Int = 180
        override val mouseX: Int = 0
        override val mouseY: Int = 0
        override val lineHeight: Int = 9

        override fun textWidth(text: String): Int = text.length * 6

        override fun wrapText(text: String, maxWidth: Int): List<String> = listOf(text)

        override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color) = Unit

        override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color) = Unit

        override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color) = Unit

        override fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean) = Unit

        override fun registerInputTarget(target: InputTarget) = Unit

        override fun withClipRect(rect: Rect, block: () -> Unit) {
            block()
        }
    }

    private class RecordingHostedElementRenderer : HostedElementRenderer by NoOpHostedElementRenderer {
        var lastButtonClick: (() -> Unit)? = null
        var lastSelectableListSelectedIndex: Int? = null
        var lastSelectableListSelectionChange: ((Int) -> Unit)? = null

        override fun drawButton(bounds: Rect, element: LayoutElement.Button) {
            lastButtonClick = element.onClick
        }

        override fun drawSelectableList(bounds: Rect, element: LayoutElement.SelectableList) {
            lastSelectableListSelectedIndex = element.selectedIndex
            lastSelectableListSelectionChange = element.onSelectedIndexChange
        }
    }
}

