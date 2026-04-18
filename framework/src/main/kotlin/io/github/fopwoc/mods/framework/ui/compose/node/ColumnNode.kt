package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

internal class ColumnNode(
    override var modifier: Modifier,
    var spacing: UiUnit,
    var horizontalAlignment: HorizontalAlignment
) : ComposeTreeNode(modifier) {
    override fun toLayoutElement(): LayoutElement = LayoutElement.Column(
        modifier = modifier,
        spacing = spacing,
        horizontalAlignment = horizontalAlignment,
        children = children.map(ComposeTreeNode::toLayoutElement)
    )
}

