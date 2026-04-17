package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal sealed class ComposeTreeNode(open var modifier: Modifier) {
    val children: MutableList<ComposeTreeNode> = mutableListOf()

    abstract fun toLayoutElement(): LayoutElement
}

