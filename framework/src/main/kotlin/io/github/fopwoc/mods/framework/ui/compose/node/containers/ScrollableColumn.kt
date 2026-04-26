package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState

internal class ScrollableColumnNode(
    override var modifier: Modifier,
    var verticalArrangement: VerticalArrangement,
    var horizontalAlignment: HorizontalAlignment,
    var state: ScrollState
) : ComposeTreeNode(modifier)

