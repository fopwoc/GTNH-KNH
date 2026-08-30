package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal class BoxNode(
    override var modifier: Modifier,
    var contentAlignment: Alignment,
) : ComposeTreeNode(modifier) {}
