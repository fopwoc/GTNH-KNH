package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

internal class SelectableListNode(
    override var modifier: Modifier,
    var items: List<String>,
    var selectedIndex: Int,
    var rowHeight: UiUnit,
    var visibleRowCount: Int,
    var onSelectedIndexChange: (Int) -> Unit,
) : ComposeTreeNode(modifier) {
  internal val hostKey = HostedWidgetKey()
}
