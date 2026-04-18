package io.github.fopwoc.mods.framework.ui.compose

import io.github.fopwoc.mods.framework.ui.compose.layout.LayoutEngine
import io.github.fopwoc.mods.framework.ui.compose.layout.InputDispatcher
import io.github.fopwoc.mods.framework.ui.compose.layout.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.columnParentData
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxParentData
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.rowParentData
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
import io.github.fopwoc.mods.framework.ui.compose.unit.UiTokens
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutEngineTest {
    @Test
    fun centersPanelAndStretchesButtonsAcrossColumnWidth() {
        val root = LayoutElement.Box(
            modifier = Modifier().fillMaxSize(),
            contentAlignment = Alignment.TopStart,
            children = listOf(
                LayoutElement.Column(
                    modifier = Modifier()
                        .width(200.uu)
                        .padding(12.uu)
                        .boxParentData(alignment = Alignment.Center),
                    verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                    horizontalAlignment = HorizontalAlignment.CENTER,
                    children = listOf(
                        LayoutElement.Text(
                            text = StyledText.of("Title"),
                            modifier = Modifier().fillMaxWidth(),
                            style = TextStyle(alignment = HorizontalAlignment.CENTER)
                        ),
                        LayoutElement.Button(
                            text = StyledText.of("Primary"),
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
            text = StyledText.of("Wrapped text should span multiple lines in a narrow container"),
            modifier = Modifier().width(72.uu).padding(2.uu),
            style = TextStyle(wrap = true)
        )

        val layout = LayoutEngine.layout(wrappedText, FakeTextMetrics(), viewportWidth = 200, viewportHeight = 200)

        assertEquals(72, layout.bounds.width)
        assertTrue(layout.bounds.height > 9 + 4)
    }

    @Test
    fun boxContentAlignmentCentersChildrenByDefaultAndChildCanOverrideIt() {
        val box = LayoutElement.Box(
            modifier = Modifier().size(80.uu),
            contentAlignment = Alignment.Center,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(10.uu)),
                LayoutElement.Spacer(modifier = Modifier().size(10.uu).boxParentData(alignment = Alignment.BottomEnd))
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
            modifier = Modifier().size(80.uu).padding(5.uu),
            contentAlignment = Alignment.Center,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(10.uu)),
                LayoutElement.Spacer(modifier = Modifier().size(60.uu).boxParentData(matchParentSize = true))
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
            modifier = Modifier(),
            contentAlignment = Alignment.Center,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(48.uu).boxParentData(matchParentSize = true))
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
            modifier = Modifier().padding(4.uu),
            contentAlignment = Alignment.Center,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(12.uu, 10.uu)),
                LayoutElement.Spacer(modifier = Modifier().size(60.uu, 8.uu).boxParentData(matchParentWidth = true))
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
            modifier = Modifier().padding(4.uu),
            contentAlignment = Alignment.Center,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(10.uu, 12.uu)),
                LayoutElement.Spacer(modifier = Modifier().size(8.uu, 60.uu).boxParentData(matchParentHeight = true))
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
            modifier = Modifier().size(40.uu).padding(5.uu),
            contentAlignment = Alignment.TopStart,
            children = listOf(
                LayoutElement.Spacer(
                    modifier = Modifier()
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
            modifier = Modifier().width(60.uu),
            horizontalArrangement = HorizontalArrangement.Center,
            verticalAlignment = VerticalAlignment.TOP,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(10.uu)),
                LayoutElement.Spacer(modifier = Modifier().size(10.uu))
            )
        )

        val layout = LayoutEngine.layout(row, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 40)

        assertEquals(20, layout.children[0].bounds.x)
        assertEquals(30, layout.children[1].bounds.x)
    }

    @Test
    fun rowChildAlignmentOverridesContainerVerticalAlignment() {
        val row = LayoutElement.Row(
            modifier = Modifier().size(40.uu),
            horizontalArrangement = HorizontalArrangement.Start,
            verticalAlignment = VerticalAlignment.TOP,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(10.uu)),
                LayoutElement.Spacer(modifier = Modifier().size(10.uu).rowParentData(alignment = VerticalAlignment.BOTTOM))
            )
        )

        val layout = LayoutEngine.layout(row, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(0, layout.children[0].bounds.y)
        assertEquals(30, layout.children[1].bounds.y)
    }

    @Test
    fun rowWeightDistributesRemainingWidthAcrossWeightedChildren() {
        val row = LayoutElement.Row(
            modifier = Modifier().size(60.uu, 20.uu),
            horizontalArrangement = HorizontalArrangement.Start,
            verticalAlignment = VerticalAlignment.TOP,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(10.uu, 10.uu)),
                LayoutElement.Spacer(modifier = Modifier().height(10.uu).rowParentData(weight = 1f)),
                LayoutElement.Spacer(modifier = Modifier().height(10.uu).rowParentData(weight = 2f))
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
            modifier = Modifier().size(40.uu, 20.uu),
            horizontalArrangement = HorizontalArrangement.Start,
            verticalAlignment = VerticalAlignment.TOP,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(10.uu, 10.uu)),
                LayoutElement.Spacer(modifier = Modifier().size(5.uu, 10.uu).rowParentData(weight = 1f, fill = false))
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
            modifier = Modifier().size(40.uu, 30.uu),
            horizontalArrangement = HorizontalArrangement.Start,
            verticalAlignment = VerticalAlignment.TOP,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(10.uu, 8.uu).rowParentData(weight = 1f))
            )
        )

        val layout = LayoutEngine.layout(row, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(40, layout.children.single().bounds.width)
        assertEquals(8, layout.children.single().bounds.height)
    }

    @Test
    fun columnSpaceBetweenArrangementDistributesExtraHeight() {
        val column = LayoutElement.Column(
            modifier = Modifier().height(60.uu),
            verticalArrangement = VerticalArrangement.SpaceBetween,
            horizontalAlignment = HorizontalAlignment.START,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().height(10.uu)),
                LayoutElement.Spacer(modifier = Modifier().height(10.uu))
            )
        )

        val layout = LayoutEngine.layout(column, FakeTextMetrics(), viewportWidth = 40, viewportHeight = 120)

        assertEquals(0, layout.children[0].bounds.y)
        assertEquals(50, layout.children[1].bounds.y)
    }

    @Test
    fun columnChildAlignmentOverridesContainerHorizontalAlignment() {
        val column = LayoutElement.Column(
            modifier = Modifier().size(40.uu),
            verticalArrangement = VerticalArrangement.Top,
            horizontalAlignment = HorizontalAlignment.START,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(10.uu)),
                LayoutElement.Spacer(modifier = Modifier().size(10.uu).columnParentData(alignment = HorizontalAlignment.END))
            )
        )

        val layout = LayoutEngine.layout(column, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(0, layout.children[0].bounds.x)
        assertEquals(30, layout.children[1].bounds.x)
    }

    @Test
    fun columnWeightDistributesRemainingHeightAcrossWeightedChildren() {
        val column = LayoutElement.Column(
            modifier = Modifier().size(20.uu, 60.uu),
            verticalArrangement = VerticalArrangement.Top,
            horizontalAlignment = HorizontalAlignment.START,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(10.uu, 10.uu)),
                LayoutElement.Spacer(modifier = Modifier().width(10.uu).columnParentData(weight = 1f)),
                LayoutElement.Spacer(modifier = Modifier().width(10.uu).columnParentData(weight = 2f))
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
            modifier = Modifier().size(20.uu, 40.uu),
            verticalArrangement = VerticalArrangement.Top,
            horizontalAlignment = HorizontalAlignment.START,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(10.uu, 10.uu)),
                LayoutElement.Spacer(modifier = Modifier().size(10.uu, 5.uu).columnParentData(weight = 1f, fill = false))
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
            modifier = Modifier(),
            style = TextStyle()
        )

        val layout = LayoutEngine.layout(text, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 40)

        assertEquals(18, layout.bounds.width)
    }

    @Test
    fun columnWeightDoesNotImplyCrossAxisFill() {
        val column = LayoutElement.Column(
            modifier = Modifier().size(30.uu, 40.uu),
            verticalArrangement = VerticalArrangement.Top,
            horizontalAlignment = HorizontalAlignment.START,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(8.uu, 10.uu).columnParentData(weight = 1f))
            )
        )

        val layout = LayoutEngine.layout(column, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(8, layout.children.single().bounds.width)
        assertEquals(40, layout.children.single().bounds.height)
    }

    @Test
    fun scrollableColumnChildAlignmentOverridesContainerHorizontalAlignment() {
        val column = LayoutElement.ScrollableColumn(
            modifier = Modifier().size(40.uu),
            verticalArrangement = VerticalArrangement.Top,
            horizontalAlignment = HorizontalAlignment.START,
            state = ScrollState(),
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(10.uu)),
                LayoutElement.Spacer(modifier = Modifier().size(10.uu).columnParentData(alignment = HorizontalAlignment.END))
            )
        )

        val layout = LayoutEngine.layout(column, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)

        assertEquals(0, layout.children[0].bounds.x)
        assertEquals(30, layout.children[1].bounds.x)
    }

    @Test
    fun scrollableColumnIgnoresWeightBecauseMainAxisIsUnbounded() {
        val column = LayoutElement.ScrollableColumn(
            modifier = Modifier().size(20.uu, 40.uu),
            verticalArrangement = VerticalArrangement.Top,
            horizontalAlignment = HorizontalAlignment.START,
            state = ScrollState(),
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(10.uu, 10.uu)),
                LayoutElement.Spacer(modifier = Modifier().size(10.uu, 5.uu).columnParentData(weight = 1f))
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
            modifier = Modifier()
                .width(140.uu)
                .height(64.uu)
                .padding(4.uu),
            verticalArrangement = VerticalArrangement.spacedBy(4.uu),
            horizontalAlignment = HorizontalAlignment.START,
            state = scrollState,
            children = List(6) { index ->
                LayoutElement.Button(
                    text = StyledText.of("Item ${index + 1}"),
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
                .width(160.uu)
                .height(72.uu)
                .padding(4.uu),
            verticalArrangement = VerticalArrangement.spacedBy(4.uu),
            horizontalAlignment = HorizontalAlignment.START,
            state = scrollState,
            children = List(10) { index ->
                LayoutElement.Button(
                    text = StyledText.of("Entry ${index + 1}"),
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
    fun tooltipModifierRegistersWholeContainerBounds() {
        val box = LayoutElement.Box(
            modifier = Modifier().size(80.uu).tooltip("Outer help"),
            contentAlignment = Alignment.Center,
            children = listOf(
                LayoutElement.Spacer(modifier = Modifier().size(10.uu))
            )
        )

        val layout = LayoutEngine.layout(box, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)
        val context = RecordingRenderContext(viewportWidth = 120, viewportHeight = 120)
        layout.draw(context)

        val tooltipTarget = InputDispatcher.findTopmostTooltipTarget(context.inputTargets, mouseX = 70, mouseY = 70)

        assertEquals(listOf("Outer help"), tooltipTarget?.tooltipLines)
    }

    @Test
    fun childTooltipOverridesParentTooltipWhenBothAreHovered() {
        val box = LayoutElement.Box(
            modifier = Modifier().size(80.uu).tooltip("Parent"),
            contentAlignment = Alignment.TopStart,
            children = listOf(
                LayoutElement.Box(
                    modifier = Modifier().size(30.uu).tooltip("Child"),
                    contentAlignment = Alignment.TopStart,
                    children = emptyList()
                )
            )
        )

        val layout = LayoutEngine.layout(box, FakeTextMetrics(), viewportWidth = 120, viewportHeight = 120)
        val context = RecordingRenderContext(viewportWidth = 120, viewportHeight = 120)
        layout.draw(context)

        val tooltipTarget = InputDispatcher.findTopmostTooltipTarget(context.inputTargets, mouseX = 10, mouseY = 10)

        assertEquals(listOf("Child"), tooltipTarget?.tooltipLines)
    }

    @Test
    fun checkboxKeepsNaturalSizeForHostedRendering() {
        val checkbox = LayoutElement.Checkbox(
            modifier = Modifier(),
            hostKey = Any(),
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
            modifier = Modifier(),
            hostKey = Any(),
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
            modifier = Modifier(),
            hostKey = Any(),
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
            modifier = Modifier(),
            hostKey = Any(),
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
            modifier = Modifier(),
            hostKey = Any(),
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
            modifier = Modifier().width(140.uu),
            hostKey = Any(),
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

