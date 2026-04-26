package io.github.fopwoc.mods.framework.ui.compose.layout.core

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.node.ComposeTreeNode

internal fun LayoutElement.isLayoutEquivalentTo(other: LayoutElement): Boolean {
    return toLayoutShape() == other.toLayoutShape() &&
        childElementsForLayoutEquivalence().areLayoutEquivalentTo(other.childElementsForLayoutEquivalence())
}

internal fun LayoutElement.isLayoutEquivalentTo(other: ComposeTreeNode): Boolean {
    return toLayoutShape() == other.toLayoutShape() &&
        childElementsForLayoutEquivalence().areLayoutEquivalentToComposeNodes(other.children)
}

internal fun ComposeTreeNode.isLayoutEquivalentTo(other: ComposeTreeNode): Boolean {
    return toLayoutShape() == other.toLayoutShape() &&
        children.areComposeLayoutEquivalentTo(other.children)
}

private fun LayoutElement.childElementsForLayoutEquivalence(): List<LayoutElement> = when (this) {
    else -> toLayoutProjection().children
}

private fun List<LayoutElement>.areLayoutEquivalentTo(other: List<LayoutElement>): Boolean {
    return size == other.size && indices.all { index ->
        this[index].isLayoutEquivalentTo(other[index])
    }
}

private fun List<LayoutElement>.areLayoutEquivalentToComposeNodes(other: List<ComposeTreeNode>): Boolean {
    return size == other.size && indices.all { index ->
        this[index].isLayoutEquivalentTo(other[index])
    }
}

private fun List<ComposeTreeNode>.areComposeLayoutEquivalentTo(other: List<ComposeTreeNode>): Boolean {
    return size == other.size && indices.all { index ->
        this[index].isLayoutEquivalentTo(other[index])
    }
}

