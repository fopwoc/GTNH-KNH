package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle

internal class TextNode(
    override var modifier: Modifier,
    var text: String,
    var style: TextStyle
) : ComposeTreeNode(modifier) {
    override fun toLayoutElement(): LayoutElement = LayoutElement.Text(
        modifier = modifier,
        text = text,
        style = style
    )
}

