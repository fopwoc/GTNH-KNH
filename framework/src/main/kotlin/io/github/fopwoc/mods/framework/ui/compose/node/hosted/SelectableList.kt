package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.text.edit.KeyModifiers
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

internal class SelectableListNode(
    override var modifier: Modifier,
    var items: List<String>,
    var selectedIndices: Set<Int>,
    var rowHeight: UiUnit,
    var visibleRowCount: Int,
    var onItemClick: (index: Int, modifiers: KeyModifiers) -> Unit,
) : ComposeTreeNode(modifier) {
  internal val hostKey = HostedWidgetKey()
  internal val scrollState = ScrollState()
}
