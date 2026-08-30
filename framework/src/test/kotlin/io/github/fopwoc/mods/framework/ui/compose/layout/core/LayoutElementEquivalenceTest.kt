package io.github.fopwoc.mods.framework.ui.compose.layout.core

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.node.ButtonNode
import io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.node.RowNode
import io.github.fopwoc.mods.framework.ui.compose.node.ScrollableColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.SelectableListNode
import io.github.fopwoc.mods.framework.ui.compose.node.SpacerNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextFieldNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextNode
import io.github.fopwoc.mods.framework.ui.compose.node.toLayoutProjection
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayoutElementEquivalenceTest {
  @Test
  fun scrollableRowElementMatchesRowNodeWithHorizontalScrollModifier() {
    val scrollState = ScrollState(initial = 7)
    val element =
        LayoutElement.ScrollableRow(
            modifier = Modifier.width(80.uu).horizontalScroll(scrollState),
            horizontalArrangement = HorizontalArrangement.spacedBy(2.uu),
            verticalAlignment = VerticalAlignment.CENTER,
            state = scrollState,
            children = emptyList(),
        )
    val node =
        RowNode(
            modifier = Modifier.width(80.uu).horizontalScroll(scrollState),
            horizontalArrangement = HorizontalArrangement.spacedBy(2.uu),
            verticalAlignment = VerticalAlignment.CENTER,
        )

    assertTrue(element.isLayoutEquivalentTo(node))
    assertTrue(node.isLayoutEquivalentTo(node))
  }

  @Test
  fun containerEquivalenceStillIncludesChildren() {
    val first =
        LayoutElement.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
            children = listOf(LayoutElement.Spacer(modifier = Modifier.size(8.uu))),
        )
    val second =
        LayoutElement.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
            children = listOf(LayoutElement.Spacer(modifier = Modifier.size(9.uu))),
        )

    assertFalse(first.isLayoutEquivalentTo(second))
  }

  @Test
  fun layoutElementProjectionKeepsShapeAndChildrenInSync() {
    val child = LayoutElement.Spacer(modifier = Modifier.size(8.uu))
    val box =
        LayoutElement.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            children = listOf(child),
        )
    val button =
        LayoutElement.Button(
            modifier = Modifier.fillMaxWidth(),
            hostKey = HostedWidgetKey(),
            text = StyledText.of("Apply"),
            enabled = true,
            onClick = {},
        )

    assertEquals(box.toLayoutShape(), box.toLayoutProjection().shape)
    assertEquals(listOf(child), box.toLayoutProjection().children)
    assertEquals(
        box.toLayoutShape(),
        box.toLayoutProjection().toLayoutElement(listOf(child)).toLayoutShape(),
    )
    assertEquals(button.toLayoutShape(), button.toLayoutProjection().shape)
    assertEquals(
        button.toLayoutShape(),
        button.toLayoutProjection().toLayoutElement().toLayoutShape(),
    )
    assertTrue(button.toLayoutProjection().children.isEmpty())
  }

  @Test
  fun columnProjectionKeepsShapeAndElementInSyncForBothScrollPaths() {
    val scrollState = ScrollState(initial = 7)
    val promoted =
        ColumnNode(
            modifier = Modifier.width(80.uu).verticalScroll(scrollState),
            verticalArrangement = VerticalArrangement.spacedBy(2.uu),
            horizontalAlignment = HorizontalAlignment.CENTER,
        )
    val explicit =
        ScrollableColumnNode(
            modifier = Modifier.width(80.uu),
            verticalArrangement = VerticalArrangement.spacedBy(2.uu),
            horizontalAlignment = HorizontalAlignment.CENTER,
            state = scrollState,
        )

    assertEquals(promoted.toLayoutShape(), promoted.toLayoutElement().toLayoutShape())
    assertEquals(explicit.toLayoutShape(), explicit.toLayoutElement().toLayoutShape())
  }

  @Test
  fun leafProjectionKeepsShapeAndElementInSync() {
    val text =
        TextNode(
            modifier = Modifier.width(80.uu),
            text = StyledText.of("Label"),
            style = TextStyle(),
        )
    val button =
        ButtonNode(
            modifier = Modifier.fillMaxWidth(),
            text = StyledText.of("Apply"),
            enabled = true,
            onClick = {},
        )
    val textField =
        TextFieldNode(
            modifier = Modifier.width(120.uu),
            state = TextFieldState(),
            placeholder = "Name",
            enabled = true,
            style = TextFieldStyle(),
        )
    val spacer = SpacerNode(modifier = Modifier.size(12.uu))

    assertEquals(text.toLayoutShape(), text.toLayoutElement().toLayoutShape())
    assertEquals(button.toLayoutShape(), button.toLayoutElement().toLayoutShape())
    assertEquals(textField.toLayoutShape(), textField.toLayoutElement().toLayoutShape())
    assertEquals(spacer.toLayoutShape(), spacer.toLayoutElement().toLayoutShape())
  }

  @Test
  fun composeLayoutProjectionKeepsShapeAndElementInSync() {
    val scrollState = ScrollState(initial = 3)
    val container =
        ColumnNode(
            modifier = Modifier.width(80.uu).verticalScroll(scrollState),
            verticalArrangement = VerticalArrangement.spacedBy(2.uu),
            horizontalAlignment = HorizontalAlignment.CENTER,
        )
    val leaf =
        ButtonNode(
            modifier = Modifier.fillMaxWidth(),
            text = StyledText.of("Apply"),
            enabled = true,
            onClick = {},
        )

    assertEquals(container.toLayoutShape(), container.toLayoutProjection().shape)
    assertEquals(
        container.toLayoutElement().toLayoutShape(),
        container.toLayoutProjection().toLayoutElement().toLayoutShape(),
    )
    assertEquals(leaf.toLayoutShape(), leaf.toLayoutProjection().shape)
    assertEquals(
        leaf.toLayoutElement().toLayoutShape(),
        leaf.toLayoutProjection().toLayoutElement().toLayoutShape(),
    )
  }

  @Test
  fun rootNodeMatchesOnlyTopStartBoxSignature() {
    val root = RootNode()
    val topStartBox =
        LayoutElement.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
            children = emptyList(),
        )
    val centeredBox =
        LayoutElement.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            children = emptyList(),
        )

    assertTrue(topStartBox.isLayoutEquivalentTo(root))
    assertFalse(centeredBox.isLayoutEquivalentTo(root))
  }

  @Test
  fun selectableListCrossModelEquivalenceStillIgnoresSelectedIndex() {
    val element =
        LayoutElement.SelectableList(
            modifier = Modifier.width(140.uu),
            hostKey = HostedWidgetKey(),
            items = listOf("Alpha", "Beta", "Gamma"),
            selectedIndex = 0,
            rowHeight = 18.uu,
            visibleRowCount = 2,
            onSelectedIndexChange = {},
        )
    val node =
        SelectableListNode(
            modifier = Modifier.width(140.uu),
            items = listOf("Alpha", "Beta", "Gamma"),
            selectedIndex = 2,
            rowHeight = 18.uu,
            visibleRowCount = 2,
            onSelectedIndexChange = {},
        )

    assertTrue(element.isLayoutEquivalentTo(node))
  }
}
