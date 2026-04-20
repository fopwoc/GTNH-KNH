package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal class RootNode : ComposeTreeNode(ROOT_MODIFIER) {
    override fun toLayoutElement(): LayoutElement {
        return LayoutElement.Box(
            modifier = ROOT_MODIFIER,
            contentAlignment = Alignment.TopStart,
            children = children.map(ComposeTreeNode::toLayoutElement)
        )
    }

    private companion object {
        val ROOT_MODIFIER: Modifier = Modifier.fillMaxSize()
    }
}

