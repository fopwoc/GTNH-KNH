package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText

internal class CheckboxNode(
    override var modifier: Modifier,
    var label: StyledText,
    var checked: Boolean,
    var enabled: Boolean,
    var onCheckedChange: (Boolean) -> Unit
) : ComposeTreeNode(modifier) {
    internal val hostKey = HostedWidgetKey()
}

