package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText

internal class CheckboxNode(
    override var modifier: Modifier,
    var label: StyledText,
    var checked: Boolean,
    var enabled: Boolean,
    var onCheckedChange: (Boolean) -> Unit
) : ComposeTreeNode(modifier) {
    private val hostKey: Any = Any()

    override fun toLayoutElement(): LayoutElement = LayoutElement.Checkbox(
        modifier = modifier,
        hostKey = hostKey,
        label = label,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange
    )
}

