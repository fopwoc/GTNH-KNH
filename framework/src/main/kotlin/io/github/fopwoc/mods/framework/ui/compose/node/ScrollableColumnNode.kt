package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

internal class ScrollableColumnNode(
    override var modifier: Modifier,
    var spacing: UiUnit,
    var horizontalAlignment: HorizontalAlignment,
    var state: ScrollState
) : ComposeTreeNode(modifier) {
    override fun toLayoutElement(): LayoutElement = LayoutElement.ScrollableColumn(
        modifier = modifier,
        spacing = spacing,
        horizontalAlignment = horizontalAlignment,
        state = state,
        children = children.map(ComposeTreeNode::toLayoutElement)
    )
}

