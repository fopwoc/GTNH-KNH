package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.horizontalScrollState

internal class RowNode(
    override var modifier: Modifier,
    var horizontalArrangement: HorizontalArrangement,
    var verticalAlignment: VerticalAlignment
) : ComposeTreeNode(modifier) {
    override fun toLayoutElement(): LayoutElement {
        val childElements = children.map(ComposeTreeNode::toLayoutElement)
        val scrollState = modifier.horizontalScrollState
        return if (scrollState != null) {
            LayoutElement.ScrollableRow(
                modifier = modifier,
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = verticalAlignment,
                state = scrollState,
                children = childElements
            )
        } else {
            LayoutElement.Row(
                modifier = modifier,
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = verticalAlignment,
                children = childElements
            )
        }
    }
}

