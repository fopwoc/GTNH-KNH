package io.github.fopwoc.mods.framework.ui.compose

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputDispatcher
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutEngine
import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutNode
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.columnParentData
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxParentData
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.rowParentData
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.node.BoxNode
import io.github.fopwoc.mods.framework.ui.compose.node.ButtonNode
import io.github.fopwoc.mods.framework.ui.compose.node.CheckboxNode
import io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.node.RowNode
import io.github.fopwoc.mods.framework.ui.compose.node.SelectableListNode
import io.github.fopwoc.mods.framework.ui.compose.node.SliderNode
import io.github.fopwoc.mods.framework.ui.compose.node.SpacerNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextFieldNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextNode
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
import io.github.fopwoc.mods.framework.ui.compose.render.NoOpHostedElementRenderer
import io.github.fopwoc.mods.framework.ui.compose.unit.UiTokens
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutEngineTest {
    @Test
    fun composeTreeLayoutMatchesEquivalentLayoutElementLayout() {
        val scrollState = ScrollState()
        val rootNode = RootNode().apply {
            children += BoxNode(
                modifier = Modifier.size(80.uu).padding(5.uu),
                contentAlignment = Alignment.Center
            ).also { boxNode ->
                boxNode.children += SpacerNode(modifier = Modifier.size(10.uu))
                boxNode.children += ColumnNode(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.uu)
                        .verticalScroll(scrollState)
                        .boxParentData(matchParentWidth = true),
                    verticalArrangement = VerticalArrangement.spacedBy(4.uu),
                    horizontalAlignment = HorizontalAlignment.START
                ).also { columnNode ->
                    columnNode.children += SpacerNode(modifier = Modifier.fillMaxWidth().height(12.uu))
                    columnNode.children += SpacerNode(modifier = Modifier.fillMaxWidth().height(12.uu))
                }
            }
        }

        val layoutElementRoot = LayoutElement.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
            children = listOf(
                LayoutElement.Box(
                    modifier = Modifier.size(80.uu).padding(5.uu),
                    contentAlignment = Alignment.Center,
                    children = listOf(
                        LayoutElement.Spacer(modifier = Modifier.size(10.uu)),
                        LayoutElement.ScrollableColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.uu)
                                .verticalScroll(scrollState)
                                .boxParentData(matchParentWidth = true),
                            verticalArrangement = VerticalArrangement.spacedBy(4.uu),
                            horizontalAlignment = HorizontalAlignment.START,
                            state = scrollState,
                            children = listOf(
                                LayoutElement.Spacer(modifier = Modifier.fillMaxWidth().height(12.uu)),
                                LayoutElement.Spacer(modifier = Modifier.fillMaxWidth().height(12.uu))
                            )
                        )
                    )
                )
            )
        )

        val nodeLayout = LayoutEngine.layout(rootNode, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)
        val elementLayout = LayoutEngine.layout(layoutElementRoot, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertLayoutTreeMatches(nodeLayout, elementLayout)
    }

    @Test
    fun composeTreeHostedLeavesMatchEquivalentLayoutElementLayout() {
        val textFieldState = TextFieldState()
        val rootNode = RootNode().apply {
            children += ColumnNode(
                modifier = Modifier.width(220.uu).padding(4.uu),
                verticalArrangement = VerticalArrangement.spacedBy(3.uu),
                horizontalAlignment = HorizontalAlignment.START
            ).also { columnNode ->
                columnNode.children += TextNode(
                    modifier = Modifier.fillMaxWidth(),
                    text = StyledText.of("Controls"),
                    style = TextStyle(alignment = HorizontalAlignment.CENTER)
                )
                columnNode.children += ButtonNode(
                    modifier = Modifier.fillMaxWidth(),
                    text = StyledText.of("Apply"),
                    enabled = true,
                    onClick = {}
                )
                columnNode.children += CheckboxNode(
                    modifier = Modifier.fillMaxWidth(),
                    label = StyledText.of("Enabled"),
                    checked = true,
                    enabled = true,
                    onCheckedChange = {}
                )
                columnNode.children += TextFieldNode(
                    modifier = Modifier.fillMaxWidth(),
                    state = textFieldState,
                    placeholder = "Name",
                    enabled = true,
                    style = TextFieldStyle()
                )
                columnNode.children += SliderNode(
                    modifier = Modifier.fillMaxWidth(),
                    value = 32.0,
                    valueRangeStart = 0.0,
                    valueRangeEnd = 100.0,
                    label = "Power",
                    suffix = "%",
                    enabled = true,
                    showDecimal = false,
                    onValueChange = {}
                )
                columnNode.children += SelectableListNode(
                    modifier = Modifier.width(140.uu),
                    items = listOf("Alpha", "Beta", "Gamma", "Delta"),
                    selectedIndex = 1,
                    rowHeight = 18.uu,
                    visibleRowCount = 3,
                    onSelectedIndexChange = {}
                )
            }
        }

        val layoutElementRoot = LayoutElement.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
            children = listOf(
                LayoutElement.Column(
                    modifier = Modifier.width(220.uu).padding(4.uu),
                    verticalArrangement = VerticalArrangement.spacedBy(3.uu),
                    horizontalAlignment = HorizontalAlignment.START,
                    children = listOf(
                        LayoutElement.Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = StyledText.of("Controls"),
                            style = TextStyle(alignment = HorizontalAlignment.CENTER)
                        ),
                        LayoutElement.Button(
                            modifier = Modifier.fillMaxWidth(),
                            hostKey = HostedWidgetKey(),
                            text = StyledText.of("Apply"),
                            enabled = true,
                            onClick = {}
                        ),
                        LayoutElement.Checkbox(
                            modifier = Modifier.fillMaxWidth(),
                            hostKey = HostedWidgetKey(),
                            label = StyledText.of("Enabled"),
                            checked = true,
                            enabled = true,
                            onCheckedChange = {}
                        ),
                        LayoutElement.TextField(
                            modifier = Modifier.fillMaxWidth(),
                            hostKey = HostedWidgetKey(),
                            state = textFieldState,
                            placeholder = "Name",
                            enabled = true,
                            style = TextFieldStyle()
                        ),
                        LayoutElement.Slider(
                            modifier = Modifier.fillMaxWidth(),
                            hostKey = HostedWidgetKey(),
                            value = 32.0,
                            valueRangeStart = 0.0,
                            valueRangeEnd = 100.0,
                            label = "Power",
                            suffix = "%",
                            enabled = true,
                            showDecimal = false,
                            onValueChange = {}
                        ),
                        LayoutElement.SelectableList(
                            modifier = Modifier.width(140.uu),
                            hostKey = HostedWidgetKey(),
                            items = listOf("Alpha", "Beta", "Gamma", "Delta"),
                            selectedIndex = 1,
                            rowHeight = 18.uu,
                            visibleRowCount = 3,
                            onSelectedIndexChange = {}
                        )
                    )
                )
            )
        )

        val nodeLayout = LayoutEngine.layout(rootNode, FakeTextMetrics(), viewportWidth = 280, viewportHeight = 220)
        val elementLayout = LayoutEngine.layout(layoutElementRoot, FakeTextMetrics(), viewportWidth = 280, viewportHeight = 220)

        assertLayoutTreeMatches(nodeLayout, elementLayout)
    }

    @Test
    fun centersPanelAndStretchesButtonsAcrossColumnWidth() {
        val root = LayoutElement.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
            children = listOf(
                LayoutElement.Column(
                    modifier = Modifier
                        .width(200.uu)
                        .padding(12.uu)
                        .boxParentData(alignment = Alignment.Center),
                    verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                    horizontalAlignment = HorizontalAlignment.CENTER,
                    children = listOf(
                        LayoutElement.Text(
                            text = StyledText.of("Title"),
                            modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(alignment = HorizontalAlignment.CENTER)
                        ),
                        LayoutElement.Button(
                            text = StyledText.of("Primary"),
                            modifier = Modifier.fillMaxWidth(),
                            hostKey = HostedWidgetKey(),
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
            text = StyledText.of("Wrapped text should span multiple lines in a narrow container"),
            modifier = Modifier.width(72.uu).padding(2.uu),
            style = TextStyle(wrap = true)
        )

        val layout = LayoutEngine.layout(wrappedText, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 200)

        assertEquals(72, layout.bounds.width)
        assertTrue(layout.bounds.height > 9 + 4)
    }

    @Test
    fun shadowedTextMeasuresOnePixelTallerThanPlainText() {
        val plain = LayoutElement.Text(
            text = StyledText.of("Shadow check"),
            modifier = Modifier.padding(2.uu),
            style = TextStyle(shadow = false)
        )
        val shadowed = LayoutElement.Text(
            text = StyledText.of("Shadow check"),
            modifier = Modifier.padding(2.uu),
            style = TextStyle(shadow = true)
        )

        val plainLayout = LayoutEngine.layout(plain, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 200)
        val shadowedLayout = LayoutEngine.layout(shadowed, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 200)

        assertEquals(plainLayout.bounds.width, shadowedLayout.bounds.width)
        assertEquals(plainLayout.bounds.height + 1, shadowedLayout.bounds.height)
    }

    @Test
    fun composeTreeShadowedTextMeasuresOnePixelTallerThanPlainText() {
        val plainRoot = RootNode().apply {
            children += TextNode(
                text = StyledText.of("Shadow check"),
                modifier = Modifier.padding(2.uu),
                style = TextStyle(shadow = false)
            )
        }
        val shadowedRoot = RootNode().apply {
            children += TextNode(
                text = StyledText.of("Shadow check"),
                modifier = Modifier.padding(2.uu),
                style = TextStyle(shadow = true)
            )
        }

        val plainLayout = LayoutEngine.layout(plainRoot, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 200)
        val shadowedLayout = LayoutEngine.layout(shadowedRoot, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 200)

        assertEquals(plainLayout.children.single().bounds.width, shadowedLayout.children.single().bounds.width)
        assertEquals(plainLayout.children.single().bounds.height + 1, shadowedLayout.children.single().bounds.height)
    }

    @Test
    fun boxContentAlignmentCentersChildrenByDefaultAndChildCanOverrideIt() {
        val box = LayoutElement.Box(
            modifier = Modifier.size(80.uu),
            contentAlignment = Alignment.Center,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(10.uu)),
                LayoutElement.Spacer(modifier = Modifier.size(10.uu).boxParentData(alignment = Alignment.BottomEnd))
            )
        )

        val layout = LayoutEngine.layout(box, FakeTextMetrics(), viewportWidth = 80, viewportHeight = 80)

        assertEquals(35, layout.children[0].bounds.x)
        assertEquals(35, layout.children[0].bounds.y)
        assertEquals(70, layout.children[1].bounds.x)
        assertEquals(70, layout.children[1].bounds.y)
    }

    @Test
    fun matchParentSizeFillsResolvedBoxContentRectWithoutAffectingNaturalSize() {
        val box = LayoutElement.Box(
            modifier = Modifier.size(80.uu).padding(5.uu),
            contentAlignment = Alignment.Center,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(10.uu)),
                LayoutElement.Spacer(modifier = Modifier.size(60.uu).boxParentData(matchParentSize = true))
            )
        )

        val layout = LayoutEngine.layout(box, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(80, layout.bounds.width)
        assertEquals(80, layout.bounds.height)
        assertEquals(35, layout.children[0].bounds.x)
        assertEquals(35, layout.children[0].bounds.y)
        assertEquals(5, layout.children[1].bounds.x)
        assertEquals(5, layout.children[1].bounds.y)
        assertEquals(70, layout.children[1].bounds.width)
        assertEquals(70, layout.children[1].bounds.height)
    }

    @Test
    fun boxWithOnlyMatchParentChildrenDoesNotGrowFromThem() {
        val box = LayoutElement.Box(
            modifier = Modifier,
            contentAlignment = Alignment.Center,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(48.uu).boxParentData(matchParentSize = true))
            )
        )

        val layout = LayoutEngine.layout(box, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(0, layout.bounds.width)
        assertEquals(0, layout.bounds.height)
        assertEquals(0, layout.children.single().bounds.width)
        assertEquals(0, layout.children.single().bounds.height)
    }

    @Test
    fun matchParentWidthFillsResolvedBoxContentWidthWithoutAffectingNaturalWidth() {
        val box = LayoutElement.Box(
            modifier = Modifier.padding(4.uu),
            contentAlignment = Alignment.Center,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(12.uu, 10.uu)),
                LayoutElement.Spacer(modifier = Modifier.size(60.uu, 8.uu).boxParentData(matchParentWidth = true))
            )
        )

        val layout = LayoutEngine.layout(box, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(20, layout.bounds.width)
        assertEquals(18, layout.bounds.height)
        assertEquals(4, layout.children[1].bounds.x)
        assertEquals(5, layout.children[1].bounds.y)
        assertEquals(12, layout.children[1].bounds.width)
        assertEquals(8, layout.children[1].bounds.height)
    }

    @Test
    fun matchParentHeightFillsResolvedBoxContentHeightWithoutAffectingNaturalHeight() {
        val box = LayoutElement.Box(
            modifier = Modifier.padding(4.uu),
            contentAlignment = Alignment.Center,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(10.uu, 12.uu)),
                LayoutElement.Spacer(modifier = Modifier.size(8.uu, 60.uu).boxParentData(matchParentHeight = true))
            )
        )

        val layout = LayoutEngine.layout(box, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(18, layout.bounds.width)
        assertEquals(20, layout.bounds.height)
        assertEquals(5, layout.children[1].bounds.x)
        assertEquals(4, layout.children[1].bounds.y)
        assertEquals(8, layout.children[1].bounds.width)
        assertEquals(12, layout.children[1].bounds.height)
    }

    @Test
    fun boxChildAlignmentAndOffsetUseExplicitBoxParentData() {
        val box = LayoutElement.Box(
            modifier = Modifier.size(40.uu).padding(5.uu),
            contentAlignment = Alignment.TopStart,
            children = listOf(
                LayoutElement.Spacer(
                    modifier = Modifier
                        .size(10.uu)
                        .offset(x = 2.uu, y = 3.uu)
                        .boxParentData(alignment = Alignment.BottomEnd)
                )
            )
        )

        val layout = LayoutEngine.layout(box, FakeTextMetrics(), viewportWidth = 80, viewportHeight = 80)

        assertEquals(27, layout.children.single().bounds.x)
        assertEquals(28, layout.children.single().bounds.y)
    }

    @Test
    fun rowCenterArrangementCentersChildrenWithinAvailableWidth() {
        val row = LayoutElement.Row(
            modifier = Modifier.width(60.uu),
            horizontalArrangement = HorizontalArrangement.Center,
            verticalAlignment = VerticalAlignment.TOP,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(10.uu)),
                LayoutElement.Spacer(modifier = Modifier.size(10.uu))
            )
        )

        val layout = LayoutEngine.layout(row, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 40)

        assertEquals(20, layout.children[0].bounds.x)
        assertEquals(30, layout.children[1].bounds.x)
    }

    @Test
    fun rowChildAlignmentOverridesContainerVerticalAlignment() {
        val row = LayoutElement.Row(
            modifier = Modifier.size(40.uu),
            horizontalArrangement = HorizontalArrangement.Start,
            verticalAlignment = VerticalAlignment.TOP,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(10.uu)),
                LayoutElement.Spacer(modifier = Modifier.size(10.uu).rowParentData(alignment = VerticalAlignment.BOTTOM))
            )
        )

        val layout = LayoutEngine.layout(row, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(0, layout.children[0].bounds.y)
        assertEquals(30, layout.children[1].bounds.y)
    }

    @Test
    fun rowWeightDistributesRemainingWidthAcrossWeightedChildren() {
        val row = LayoutElement.Row(
            modifier = Modifier.size(60.uu, 20.uu),
            horizontalArrangement = HorizontalArrangement.Start,
            verticalAlignment = VerticalAlignment.TOP,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(10.uu, 10.uu)),
                LayoutElement.Spacer(modifier = Modifier.height(10.uu).rowParentData(weight = 1f)),
                LayoutElement.Spacer(modifier = Modifier.height(10.uu).rowParentData(weight = 2f))
            )
        )

        val layout = LayoutEngine.layout(row, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(60, layout.bounds.width)
        assertEquals(10, layout.children[1].bounds.x)
        assertEquals(16, layout.children[1].bounds.width)
        assertEquals(26, layout.children[2].bounds.x)
        assertEquals(34, layout.children[2].bounds.width)
    }

    @Test
    fun rowWeightWithFillFalseReservesSlotWithoutForcingChildWidth() {
        val row = LayoutElement.Row(
            modifier = Modifier.size(40.uu, 20.uu),
            horizontalArrangement = HorizontalArrangement.Start,
            verticalAlignment = VerticalAlignment.TOP,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(10.uu, 10.uu)),
                LayoutElement.Spacer(modifier = Modifier.size(5.uu, 10.uu).rowParentData(weight = 1f, fill = false))
            )
        )

        val layout = LayoutEngine.layout(row, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(40, layout.bounds.width)
        assertEquals(10, layout.children[1].bounds.x)
        assertEquals(5, layout.children[1].bounds.width)
    }

    @Test
    fun rowWeightDoesNotImplyCrossAxisFill() {
        val row = LayoutElement.Row(
            modifier = Modifier.size(40.uu, 30.uu),
            horizontalArrangement = HorizontalArrangement.Start,
            verticalAlignment = VerticalAlignment.TOP,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(10.uu, 8.uu).rowParentData(weight = 1f))
            )
        )

        val layout = LayoutEngine.layout(row, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(40, layout.children.single().bounds.width)
        assertEquals(8, layout.children.single().bounds.height)
    }

    @Test
    fun columnSpaceBetweenArrangementDistributesExtraHeight() {
        val column = LayoutElement.Column(
            modifier = Modifier.height(60.uu),
            verticalArrangement = VerticalArrangement.SpaceBetween,
            horizontalAlignment = HorizontalAlignment.START,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.height(10.uu)),
                LayoutElement.Spacer(modifier = Modifier.height(10.uu))
            )
        )

        val layout = LayoutEngine.layout(column, FakeTextMetrics(), viewportWidth = 40, viewportHeight = 120)

        assertEquals(0, layout.children[0].bounds.y)
        assertEquals(50, layout.children[1].bounds.y)
    }

    @Test
    fun columnChildAlignmentOverridesContainerHorizontalAlignment() {
        val column = LayoutElement.Column(
            modifier = Modifier.size(40.uu),
            verticalArrangement = VerticalArrangement.Top,
            horizontalAlignment = HorizontalAlignment.START,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(10.uu)),
                LayoutElement.Spacer(modifier = Modifier.size(10.uu).columnParentData(alignment = HorizontalAlignment.END))
            )
        )

        val layout = LayoutEngine.layout(column, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(0, layout.children[0].bounds.x)
        assertEquals(30, layout.children[1].bounds.x)
    }

    @Test
    fun columnWeightDistributesRemainingHeightAcrossWeightedChildren() {
        val column = LayoutElement.Column(
            modifier = Modifier.size(20.uu, 60.uu),
            verticalArrangement = VerticalArrangement.Top,
            horizontalAlignment = HorizontalAlignment.START,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(10.uu, 10.uu)),
                LayoutElement.Spacer(modifier = Modifier.width(10.uu).columnParentData(weight = 1f)),
                LayoutElement.Spacer(modifier = Modifier.width(10.uu).columnParentData(weight = 2f))
            )
        )

        val layout = LayoutEngine.layout(column, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(60, layout.bounds.height)
        assertEquals(10, layout.children[1].bounds.y)
        assertEquals(16, layout.children[1].bounds.height)
        assertEquals(26, layout.children[2].bounds.y)
        assertEquals(34, layout.children[2].bounds.height)
    }

    @Test
    fun columnWeightWithFillFalseReservesSlotWithoutForcingChildHeight() {
        val column = LayoutElement.Column(
            modifier = Modifier.size(20.uu, 40.uu),
            verticalArrangement = VerticalArrangement.Top,
            horizontalAlignment = HorizontalAlignment.START,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(10.uu, 10.uu)),
                LayoutElement.Spacer(modifier = Modifier.size(10.uu, 5.uu).columnParentData(weight = 1f, fill = false))
            )
        )

        val layout = LayoutEngine.layout(column, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(40, layout.bounds.height)
        assertEquals(10, layout.children[1].bounds.y)
        assertEquals(5, layout.children[1].bounds.height)
    }

    @Test
    fun richTextMeasurementUsesVisibleGlyphWidthInsteadOfFormattingCodes() {
        val text = LayoutElement.Text(
            text = styledText {
                append("A")
                withColor(MinecraftColor.Red) {
                    append("B")
                }
                append("C")
            },
            modifier = Modifier,
            style = TextStyle()
        )

        val layout = LayoutEngine.layout(text, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 40)

        assertEquals(18, layout.bounds.width)
    }

    @Test
    fun composeTreeRichTextMeasurementUsesVisibleGlyphWidthInsteadOfFormattingCodes() {
        val root = RootNode().apply {
            children += TextNode(
                text = styledText {
                    append("A")
                    withColor(MinecraftColor.Red) {
                        append("B")
                    }
                    append("C")
                },
                modifier = Modifier,
                style = TextStyle()
            )
        }

        val layout = LayoutEngine.layout(root, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 40)

        assertEquals(18, layout.children.single().bounds.width)
    }

    @Test
    fun columnWeightDoesNotImplyCrossAxisFill() {
        val column = LayoutElement.Column(
            modifier = Modifier.size(30.uu, 40.uu),
            verticalArrangement = VerticalArrangement.Top,
            horizontalAlignment = HorizontalAlignment.START,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(8.uu, 10.uu).columnParentData(weight = 1f))
            )
        )

        val layout = LayoutEngine.layout(column, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(8, layout.children.single().bounds.width)
        assertEquals(40, layout.children.single().bounds.height)
    }

    @Test
    fun scrollableColumnChildAlignmentOverridesContainerHorizontalAlignment() {
        val column = LayoutElement.ScrollableColumn(
            modifier = Modifier.size(40.uu),
            verticalArrangement = VerticalArrangement.Top,
            horizontalAlignment = HorizontalAlignment.START,
            state = ScrollState(),
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(10.uu)),
                LayoutElement.Spacer(modifier = Modifier.size(10.uu).columnParentData(alignment = HorizontalAlignment.END))
            )
        )

        val layout = LayoutEngine.layout(column, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(0, layout.children[0].bounds.x)
        assertEquals(30, layout.children[1].bounds.x)
    }

    @Test
    fun scrollableColumnIgnoresWeightBecauseMainAxisIsUnbounded() {
        val column = LayoutElement.ScrollableColumn(
            modifier = Modifier.size(20.uu, 40.uu),
            verticalArrangement = VerticalArrangement.Top,
            horizontalAlignment = HorizontalAlignment.START,
            state = ScrollState(),
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(10.uu, 10.uu)),
                LayoutElement.Spacer(modifier = Modifier.size(10.uu, 5.uu).columnParentData(weight = 1f))
            )
        )

        val layout = LayoutEngine.layout(column, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(10, layout.children[1].bounds.y)
        assertEquals(5, layout.children[1].bounds.height)
    }

    @Test
    fun scrollColumnConsumesWheelInputAndUpdatesScrollState() {
        val scrollState = ScrollState()
        val scrollColumn = LayoutElement.ScrollableColumn(
            modifier = Modifier
                .width(140.uu)
                .height(64.uu)
                .padding(4.uu),
            verticalArrangement = VerticalArrangement.spacedBy(4.uu),
            horizontalAlignment = HorizontalAlignment.START,
            state = scrollState,
            children = List(6) { index ->
                LayoutElement.Button(
                    text = StyledText.of("Item ${index + 1}"),
                    modifier = Modifier.fillMaxWidth(),
                    hostKey = HostedWidgetKey(),
                    enabled = true,
                    onClick = {}
                )
            }
        )

        val layout = LayoutEngine.layout(scrollColumn, FakeTextMetrics(), viewportWidth = 140, viewportHeight = 64)
        val context = RecordingRenderContext(viewportWidth = 140, viewportHeight = 64)
        layout.draw(context, NoOpHostedElementRenderer)
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
            modifier = Modifier
                .width(160.uu)
                .height(72.uu)
                .padding(4.uu),
            verticalArrangement = VerticalArrangement.spacedBy(4.uu),
            horizontalAlignment = HorizontalAlignment.START,
            state = scrollState,
            children = List(10) { index ->
                LayoutElement.Button(
                    text = StyledText.of("Entry ${index + 1}"),
                    modifier = Modifier.fillMaxWidth(),
                    hostKey = HostedWidgetKey(),
                    enabled = true,
                    onClick = {}
                )
            }
        )

        val layout = LayoutEngine.layout(scrollColumn, FakeTextMetrics(), viewportWidth = 160, viewportHeight = 72)
        val context = RecordingRenderContext(viewportWidth = 160, viewportHeight = 72)
        layout.draw(context, NoOpHostedElementRenderer)
        val pressTarget = InputDispatcher.findTopmostPressTarget(context.inputTargets, mouseX = 151, mouseY = 8)
        val pressResult = pressTarget?.onPress?.invoke(151, 8, 0)
        val drag = pressResult?.session

        assertTrue(pressResult?.consumed == true)
        assertTrue(drag != null)
        assertTrue(drag.onDrag(156, 48))
        assertTrue(scrollState.value > 0)
    }

    @Test
    fun scrollRowConsumesWheelInputAndUpdatesScrollState() {
        val scrollState = ScrollState()
        val scrollRow = LayoutElement.ScrollableRow(
            modifier = Modifier
                .width(72.uu)
                .height(40.uu)
                .padding(4.uu),
            horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
            verticalAlignment = VerticalAlignment.TOP,
            state = scrollState,
            children = List(6) { index ->
                LayoutElement.Button(
                    text = StyledText.of("Item ${index + 1}"),
                    modifier = Modifier.width(40.uu),
                    hostKey = HostedWidgetKey(),
                    enabled = true,
                    onClick = {}
                )
            }
        )

        val layout = LayoutEngine.layout(scrollRow, FakeTextMetrics(), viewportWidth = 72, viewportHeight = 40)
        val context = RecordingRenderContext(viewportWidth = 72, viewportHeight = 40)
        layout.draw(context, NoOpHostedElementRenderer)
        val wheelTarget = InputDispatcher.findTopmostWheelTarget(context.inputTargets, mouseX = 10, mouseY = 10)
        val handled = wheelTarget?.onWheel?.invoke(10, 10, -120) == true

        assertTrue(handled)
        assertTrue(scrollState.value > 0)
        assertTrue(scrollState.maxValue > 0)
    }

    @Test
    fun scrollRowScrollbarCanBeDragged() {
        val scrollState = ScrollState()
        val scrollRow = LayoutElement.ScrollableRow(
            modifier = Modifier
                .width(88.uu)
                .height(40.uu)
                .padding(4.uu),
            horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
            verticalAlignment = VerticalAlignment.TOP,
            state = scrollState,
            children = List(8) { index ->
                LayoutElement.Button(
                    text = StyledText.of("Entry ${index + 1}"),
                    modifier = Modifier.width(36.uu),
                    hostKey = HostedWidgetKey(),
                    enabled = true,
                    onClick = {}
                )
            }
        )

        val layout = LayoutEngine.layout(scrollRow, FakeTextMetrics(), viewportWidth = 88, viewportHeight = 40)
        val context = RecordingRenderContext(viewportWidth = 88, viewportHeight = 40)
        layout.draw(context, NoOpHostedElementRenderer)
        val thumbTarget = context.inputTargets.first { it.kind == InputTargetKind.SCROLL_THUMB }
        val thumbCenterX = thumbTarget.bounds.x + (thumbTarget.bounds.width / 2)
        val thumbCenterY = thumbTarget.bounds.y + (thumbTarget.bounds.height / 2)
        val pressResult = thumbTarget.onPress?.invoke(thumbCenterX, thumbCenterY, 0)
        val drag = pressResult?.session

        assertTrue(pressResult?.consumed == true)
        assertTrue(drag != null)
        assertTrue(drag.onDrag(thumbCenterX + 24, thumbCenterY))
        assertTrue(scrollState.value > 0)
    }

    @Test
    fun tooltipModifierRegistersWholeContainerBounds() {
        val box = LayoutElement.Box(
            modifier = Modifier.size(80.uu).tooltip("Outer help"),
            contentAlignment = Alignment.Center,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier.size(10.uu))
            )
        )

        val layout = LayoutEngine.layout(box, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)
        val context = RecordingRenderContext(viewportWidth = 120, viewportHeight = 120)
        layout.draw(context, NoOpHostedElementRenderer)

        val tooltipTarget = InputDispatcher.findTopmostTooltipTarget(context.inputTargets, mouseX = 70, mouseY = 70)

        assertEquals(listOf("Outer help"), tooltipTarget?.tooltipLines)
    }

    @Test
    fun childTooltipOverridesParentTooltipWhenBothAreHovered() {
        val box = LayoutElement.Box(
            modifier = Modifier.size(80.uu).tooltip("Parent"),
            contentAlignment = Alignment.TopStart,
            children = listOf(
                LayoutElement.Box(
                    modifier = Modifier.size(30.uu).tooltip("Child"),
                    contentAlignment = Alignment.TopStart,
                    children = emptyList()
                )
            )
        )

        val layout = LayoutEngine.layout(box, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)
        val context = RecordingRenderContext(viewportWidth = 120, viewportHeight = 120)
        layout.draw(context, NoOpHostedElementRenderer)

        val tooltipTarget = InputDispatcher.findTopmostTooltipTarget(context.inputTargets, mouseX = 10, mouseY = 10)

        assertEquals(listOf("Child"), tooltipTarget?.tooltipLines)
    }

    @Test
    fun checkboxKeepsNaturalSizeForHostedRendering() {
        val checkbox = LayoutElement.Checkbox(
            modifier = Modifier,
            hostKey = HostedWidgetKey(),
            label = StyledText.of("Native"),
            checked = false,
            enabled = true,
            onCheckedChange = {}
        )

        val layout = LayoutEngine.layout(checkbox, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 40)

        assertTrue(layout.bounds.width >= 13 + ("Native".length * 6))
        assertTrue(layout.bounds.height >= 11)
    }

    @Test
    fun styledButtonUsesVisibleGlyphWidthForNaturalSize() {
        val button = LayoutElement.Button(
            modifier = Modifier,
            hostKey = HostedWidgetKey(),
            text = styledText {
                append("Open ")
                withColor(MinecraftColor.Gold) {
                    append("Menu")
                }
            },
            enabled = true,
            onClick = {}
        )

        val layout = LayoutEngine.layout(button, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 80)

        assertEquals(98, layout.bounds.width)
    }

    @Test
    fun styledCheckboxUsesVisibleGlyphWidthForNaturalSize() {
        val checkbox = LayoutElement.Checkbox(
            modifier = Modifier,
            hostKey = HostedWidgetKey(),
            label = styledText {
                withBold {
                    append("Native")
                }
            },
            checked = false,
            enabled = true,
            onCheckedChange = {}
        )

        val layout = LayoutEngine.layout(checkbox, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 40)

        assertTrue(layout.bounds.width >= 13 + ("Native".length * 6))
    }

    @Test
    fun textFieldUsesExpectedNaturalControlSize() {
        val textField = LayoutElement.TextField(
            modifier = Modifier,
            hostKey = HostedWidgetKey(),
            state = TextFieldState(),
            placeholder = "Name",
            enabled = true,
            style = TextFieldStyle()
        )

        val layout = LayoutEngine.layout(textField, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 80)

        assertEquals(120, layout.bounds.width)
        assertEquals(UiTokens.ControlHeight.resolved, layout.bounds.height)
    }

    @Test
    fun sliderUsesExpectedNaturalControlSize() {
        val slider = LayoutElement.Slider(
            modifier = Modifier,
            hostKey = HostedWidgetKey(),
            value = 32.0,
            valueRangeStart = 0.0,
            valueRangeEnd = 100.0,
            label = "Power",
            suffix = "",
            enabled = true,
            showDecimal = false,
            onValueChange = {}
        )

        val layout = LayoutEngine.layout(slider, FakeTextMetrics(), viewportWidth = 240, viewportHeight = 80)

        assertEquals(150, layout.bounds.width)
        assertEquals(UiTokens.ControlHeight.resolved, layout.bounds.height)
    }

    @Test
    fun selectableListUsesVisibleRowCountForNaturalHeight() {
        val selectableList = LayoutElement.SelectableList(
            modifier = Modifier.width(140.uu),
            hostKey = HostedWidgetKey(),
            items = listOf("Alpha", "Beta", "Gamma", "Delta"),
            selectedIndex = 2,
            rowHeight = 18.uu,
            visibleRowCount = 3,
            onSelectedIndexChange = {}
        )

        val layout = LayoutEngine.layout(selectableList, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 200)

        assertEquals(140, layout.bounds.width)
        assertEquals(62, layout.bounds.height)
    }

    @Test
    fun nestedScrollableListCardsCanScrollLastButtonFullyIntoView() {
        val scrollState = ScrollState()
        val scrollable = ColumnNode(
            modifier = Modifier
                .width(220.uu)
                .height(176.uu)
                .padding(6.uu)
                .background(Color(0x8821262F))
                .border(Color(0xFF59606E))
                .verticalScroll(scrollState),
            verticalArrangement = VerticalArrangement.spacedBy(4.uu),
            horizontalAlignment = HorizontalAlignment.START
        ).apply {
            repeat(5) { index ->
                children += BoxNode(
                    modifier = Modifier.fillMaxWidth().background(Color(0xB0141418)).border(Color(0xFF4A4A56)),
                    contentAlignment = Alignment.TopStart,
                ).also { panel ->
                    panel.children += BoxNode(
                        modifier = Modifier.padding(UiTokens.PanelPadding),
                        contentAlignment = Alignment.TopStart
                    ).also { paddedContent ->
                        paddedContent.children += ColumnNode(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = VerticalArrangement.spacedBy(4.uu),
                            horizontalAlignment = HorizontalAlignment.START
                        ).also { cardColumn ->
                            cardColumn.children += TextNode(
                                modifier = Modifier.fillMaxWidth(),
                                text = StyledText.of("North Pantry #$index"),
                                style = TextStyle(wrap = true, color = Color(0xFFF1D7A8))
                            )
                            cardColumn.children += TextNode(
                                modifier = Modifier.fillMaxWidth(),
                                text = StyledText.of("Cras mattis consectetur purus sit amet fermentum, stacked with flour sacks and scribbled shopping lists that should wrap in narrow viewports."),
                                style = TextStyle(wrap = true, color = Color.rgb(red = 0xE6, green = 0xE6, blue = 0xE6))
                            )
                            cardColumn.children += ButtonNode(
                                modifier = Modifier.fillMaxWidth(),
                                text = StyledText.of("Keep this line ${index + 1}"),
                                enabled = true,
                                onClick = {}
                            )
                        }
                    }
                }
            }
        }

        val root = RootNode().apply {
            children += scrollable
        }

        LayoutEngine.layout(root, FakeTextMetrics(), viewportWidth = 220, viewportHeight = 176)
        assertTrue(scrollState.maxValue > 0)

        scrollState.scrollTo(scrollState.maxValue)
        val scrolledLayout = LayoutEngine.layout(root, FakeTextMetrics(), viewportWidth = 220, viewportHeight = 176)
        val scrollableLayout = scrolledLayout.children.single()

        val viewport = scrollableLayout.bounds.inset(scrollable.modifier.padding)
        val lastButton = scrolledLayout.descendants().last { it.element is LayoutElement.Button }

        assertTrue(lastButton.bounds.y >= viewport.y, "lastButton=${lastButton.bounds}, viewport=$viewport, max=${scrollState.maxValue}")
        assertTrue(
            lastButton.bounds.y + lastButton.bounds.height <= viewport.y + viewport.height,
            "lastButton=${lastButton.bounds}, viewport=$viewport, max=${scrollState.maxValue}"
        )
    }

    @Test
    fun outerScrollableColumnMeasuresTallNestedColumnAtNaturalHeight() {
        val scrollState = ScrollState()
        val root = RootNode().apply {
            children += ColumnNode(
                modifier = Modifier
                    .width(220.uu)
                    .height(120.uu)
                    .verticalScroll(scrollState),
                verticalArrangement = VerticalArrangement.spacedBy(4.uu),
                horizontalAlignment = HorizontalAlignment.START
            ).also { scrollColumn ->
                scrollColumn.children += ColumnNode(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = VerticalArrangement.spacedBy(4.uu),
                    horizontalAlignment = HorizontalAlignment.START
                ).also { tallContent ->
                    repeat(6) { index ->
                        tallContent.children += TextNode(
                            modifier = Modifier.fillMaxWidth(),
                            text = StyledText.of("Tall section ${index + 1} with enough text to need its full measured height"),
                            style = TextStyle(wrap = true)
                        )
                        tallContent.children += SpacerNode(modifier = Modifier.height(20.uu))
                    }
                }
            }
        }

        LayoutEngine.layout(root, FakeTextMetrics(), viewportWidth = 220, viewportHeight = 120)
        assertTrue(scrollState.maxValue > 0, "max=${scrollState.maxValue}")

        scrollState.scrollTo(scrollState.maxValue)
        val scrolledLayout = LayoutEngine.layout(root, FakeTextMetrics(), viewportWidth = 220, viewportHeight = 120)
        val scrollableLayout = scrolledLayout.children.single()
        val viewport = scrollableLayout.bounds
        val lastSpacer = scrolledLayout.descendants().last { it.element is LayoutElement.Spacer }

        assertTrue(lastSpacer.bounds.y >= viewport.y, "lastSpacer=${lastSpacer.bounds}, viewport=$viewport, max=${scrollState.maxValue}")
        assertTrue(
            lastSpacer.bounds.y + lastSpacer.bounds.height <= viewport.y + viewport.height,
            "lastSpacer=${lastSpacer.bounds}, viewport=$viewport, max=${scrollState.maxValue}"
        )
    }

    @Test
    fun outerScrollableRowMeasuresWideNestedRowAtNaturalWidth() {
        val scrollState = ScrollState()
        val root = RootNode().apply {
            children += RowNode(
                modifier = Modifier
                    .width(120.uu)
                    .height(60.uu)
                    .horizontalScroll(scrollState),
                horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
                verticalAlignment = VerticalAlignment.TOP
            ).also { scrollRow ->
                scrollRow.children += RowNode(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
                    verticalAlignment = VerticalAlignment.TOP
                ).also { wideContent ->
                    repeat(6) { index ->
                        wideContent.children += SpacerNode(modifier = Modifier.width(36.uu).height(20.uu))
                        wideContent.children += TextNode(
                            modifier = Modifier,
                            text = StyledText.of("Wide ${index + 1}"),
                            style = TextStyle()
                        )
                    }
                }
            }
        }

        LayoutEngine.layout(root, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 60)
        assertTrue(scrollState.maxValue > 0, "max=${scrollState.maxValue}")

        scrollState.scrollTo(scrollState.maxValue)
        val scrolledLayout = LayoutEngine.layout(root, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 60)
        val scrollableLayout = scrolledLayout.children.single()
        val viewport = scrollableLayout.bounds
        val lastText = scrolledLayout.descendants().last { it.element is LayoutElement.Text }

        assertTrue(lastText.bounds.x >= viewport.x, "lastText=${lastText.bounds}, viewport=$viewport, max=${scrollState.maxValue}")
        assertTrue(
            lastText.bounds.x + lastText.bounds.width <= viewport.x + viewport.width,
            "lastText=${lastText.bounds}, viewport=$viewport, max=${scrollState.maxValue}"
        )
    }

    private class FakeTextMetrics : TextMetrics {
        override val lineHeight: Int = 9

        override fun textWidth(text: String): Int = stripFormatting(text).length * 6

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

        private fun stripFormatting(text: String): String {
            return text.replace(Regex("(?i)\\u00a7[0-9A-FK-OR]"), "")
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

        override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color) = Unit

        override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color) = Unit

        override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color) = Unit

        override fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean) = Unit


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

    private fun LayoutNode.descendants(): Sequence<LayoutNode> = sequence {
        yield(this@descendants)
        children.forEach { child ->
            yieldAll(child.descendants())
        }
    }

    private fun assertLayoutTreeMatches(actual: io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutNode, expected: io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutNode) {
        assertEquals(expected.element::class, actual.element::class)
        assertEquals(expected.bounds, actual.bounds)
        assertEquals(expected.children.size, actual.children.size)
        actual.children.indices.forEach { index ->
            assertLayoutTreeMatches(actual.children[index], expected.children[index])
        }
    }
}
