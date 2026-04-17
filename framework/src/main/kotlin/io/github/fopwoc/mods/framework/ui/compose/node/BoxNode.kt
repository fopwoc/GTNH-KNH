package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal class BoxNode(
    override var modifier: Modifier
) : ComposeTreeNode(modifier) {
    override fun toLayoutElement(): LayoutElement = LayoutElement.Box(
        modifier = modifier,
        children = children.map(ComposeTreeNode::toLayoutElement)
    )
}

