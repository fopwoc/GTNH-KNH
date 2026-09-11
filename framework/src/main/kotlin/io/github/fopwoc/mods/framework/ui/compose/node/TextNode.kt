package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText

internal class TextNode(
    override var modifier: Modifier,
    var text: StyledText,
    var style: TextStyle,
) : ComposeTreeNode(modifier)
