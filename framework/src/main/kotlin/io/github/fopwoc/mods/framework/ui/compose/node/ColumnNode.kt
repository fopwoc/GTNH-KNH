package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal class ColumnNode(
    override var modifier: Modifier,
    var verticalArrangement: VerticalArrangement,
    var horizontalAlignment: HorizontalAlignment,
) : ComposeTreeNode(modifier) {}
