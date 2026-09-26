package io.github.fopwoc.mods.framework.ui.compose.layout.core

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.BoxNode
import io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.node.RowNode
import io.github.fopwoc.mods.framework.ui.compose.node.ScrollableColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.SelectableListNode
import io.github.fopwoc.mods.framework.ui.compose.node.SpacerNode
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LayoutShapeTest {
    @Test
    fun aRowWithAHorizontalScrollModifierLaysOutAsAScrollableRow() {
        val scrollState = ScrollState(initial = 7)
        val row =
            RowNode(
                modifier = Modifier.width(80.uu).horizontalScroll(scrollState),
                horizontalArrangement = HorizontalArrangement.spacedBy(2.uu),
                verticalAlignment = VerticalAlignment.CENTER,
            )

        assertTrue(row.toLayoutShape() is LayoutShape.ScrollableRow)
    }

    @Test
    fun bothWaysToAScrollingColumnLayOutAlike() {
        val scrollState = ScrollState(initial = 7)
        val promoted =
            ColumnNode(
                modifier = Modifier.width(80.uu).verticalScroll(scrollState),
                verticalArrangement = VerticalArrangement.spacedBy(2.uu),
                horizontalAlignment = HorizontalAlignment.CENTER,
            )
        val explicit =
            ScrollableColumnNode(
                modifier = Modifier.width(80.uu).verticalScroll(scrollState),
                verticalArrangement = VerticalArrangement.spacedBy(2.uu),
                horizontalAlignment = HorizontalAlignment.CENTER,
                state = scrollState,
            )

        assertTrue(promoted.toLayoutShape() is LayoutShape.ScrollableColumn)
        assertEquals(promoted.toLayoutShape(), explicit.toLayoutShape())
    }

    @Test
    fun theRootLaysOutAsAFullSizeTopStartBox() {
        val root = RootNode()

        assertEquals(
            BoxNode(Modifier.fillMaxSize(), Alignment.TopStart).toLayoutShape(),
            root.toLayoutShape(),
        )
        assertNotEquals(
            BoxNode(Modifier.fillMaxSize(), Alignment.Center).toLayoutShape(),
            root.toLayoutShape(),
        )
    }

    @Test
    fun sizesChangeTheShapeButSelectionDoesNot() {
        assertNotEquals(
            SpacerNode(Modifier.size(8.uu)).toLayoutShape(),
            SpacerNode(Modifier.size(9.uu)).toLayoutShape(),
        )
        fun list(selected: Int) =
            SelectableListNode(
                modifier = Modifier.width(140.uu),
                items = listOf("Alpha", "Beta", "Gamma"),
                selectedIndices = setOf(selected),
                rowHeight = 18.uu,
                visibleRowCount = 2,
                onItemClick = { _, _ -> },
            )
        assertEquals(list(0).toLayoutShape(), list(2).toLayoutShape())
    }
}
