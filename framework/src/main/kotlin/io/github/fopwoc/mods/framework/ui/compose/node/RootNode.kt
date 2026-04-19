package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal class RootNode : ComposeTreeNode(Modifier.fillMaxSize()) {
    override fun toLayoutElement(): LayoutElement {
        return LayoutElement.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
            children = children.map(ComposeTreeNode::toLayoutElement)
        )
    }
}

