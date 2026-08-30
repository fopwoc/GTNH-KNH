package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal class RowNode(
    override var modifier: Modifier,
    var horizontalArrangement: HorizontalArrangement,
    var verticalAlignment: VerticalAlignment,
) : ComposeTreeNode(modifier) {}
