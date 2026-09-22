package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.state.LazyListState
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

/** Children are the composed window starting at [firstIndex]; [itemCount] sizes the content. */
internal class LazyColumnNode(
    override var modifier: Modifier,
    var state: LazyListState,
    var itemHeight: UiUnit?,
    var itemCount: Int,
    var firstIndex: Int,
) : ComposeTreeNode(modifier)
