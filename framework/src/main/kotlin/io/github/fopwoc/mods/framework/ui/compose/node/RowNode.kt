package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal class RowNode(
    override var modifier: Modifier,
    var spacing: Int,
    var verticalAlignment: VerticalAlignment
) : ComposeTreeNode(modifier) {
    override fun toLayoutElement(): LayoutElement = LayoutElement.Row(
        modifier = modifier,
        spacing = spacing,
        verticalAlignment = verticalAlignment,
        children = children.map(ComposeTreeNode::toLayoutElement)
    )
}

