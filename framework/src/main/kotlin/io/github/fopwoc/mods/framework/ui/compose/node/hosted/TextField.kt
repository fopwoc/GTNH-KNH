package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState

internal class TextFieldNode(
    override var modifier: Modifier,
    var state: TextFieldState,
    var placeholder: String,
    var enabled: Boolean,
    var style: TextFieldStyle,
) : ComposeTreeNode(modifier) {
  internal val hostKey = HostedWidgetKey()
}
