package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState

internal class TextFieldNode(
    override var modifier: Modifier,
    var state: TextFieldState,
    var placeholder: String,
    var enabled: Boolean,
    var style: TextFieldStyle
) : ComposeTreeNode(modifier) {
    override fun toLayoutElement(): LayoutElement = LayoutElement.TextField(
        modifier = modifier,
        state = state,
        placeholder = placeholder,
        enabled = enabled,
        style = style
    )
}

