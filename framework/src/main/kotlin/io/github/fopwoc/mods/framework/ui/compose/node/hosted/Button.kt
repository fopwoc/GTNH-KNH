package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText

internal class ButtonNode(
    override var modifier: Modifier,
    var text: StyledText,
    var enabled: Boolean,
    var onClick: () -> Unit,
) : ComposeTreeNode(modifier) {}
