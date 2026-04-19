package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.verticalScrollState

internal class ColumnNode(
    override var modifier: Modifier,
    var verticalArrangement: VerticalArrangement,
    var horizontalAlignment: HorizontalAlignment
) : ComposeTreeNode(modifier) {
    override fun toLayoutElement(): LayoutElement {
        val childElements = children.map(ComposeTreeNode::toLayoutElement)
        val scrollState = modifier.verticalScrollState
        return if (scrollState != null) {
            LayoutElement.ScrollableColumn(
                modifier = modifier,
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment,
                state = scrollState,
                children = childElements
            )
        } else {
            LayoutElement.Column(
                modifier = modifier,
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment,
                children = childElements
            )
        }
    }
}

