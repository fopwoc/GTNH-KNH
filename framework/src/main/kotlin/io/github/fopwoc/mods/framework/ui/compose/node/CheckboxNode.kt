package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal class CheckboxNode(
    override var modifier: Modifier,
    var label: String,
    var checked: Boolean,
    var enabled: Boolean,
    var onCheckedChange: (Boolean) -> Unit
) : ComposeTreeNode(modifier) {
    override fun toLayoutElement(): LayoutElement = LayoutElement.Checkbox(
        modifier = modifier,
        label = label,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange
    )
}

