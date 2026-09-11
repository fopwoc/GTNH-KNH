package io.github.fopwoc.mods.framework.ui.compose.component.native

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.remember
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.SelectableListNode
import io.github.fopwoc.mods.framework.ui.compose.text.edit.KeyModifiers
import io.github.fopwoc.mods.framework.ui.compose.unit.UiTokens
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

object SelectableListDefaults {
  val RowHeight: UiUnit = UiTokens.Slot
  const val VisibleRowCount: Int = 6
}

/** Single-selection list; a click replaces the selection. */
@Composable
fun SelectableList(
    items: List<String>,
    selectedIndex: Int = -1,
    modifier: Modifier = Modifier,
    rowHeight: UiUnit = SelectableListDefaults.RowHeight,
    visibleRowCount: Int = SelectableListDefaults.VisibleRowCount,
    onSelectedIndexChange: (Int) -> Unit,
) {
  SelectableListHost(
      items = items,
      selectedIndices = if (selectedIndex in items.indices) setOf(selectedIndex) else emptySet(),
      modifier = modifier,
      rowHeight = rowHeight,
      visibleRowCount = visibleRowCount,
      onItemClick = { index, _ -> onSelectedIndexChange(index) },
  )
}

/**
 * Multi-selection list with desktop conventions: click selects one, Ctrl+click toggles, Shift+click
 * extends from the last clicked row.
 */
@Composable
fun MultiSelectableList(
    items: List<String>,
    selectedIndices: Set<Int>,
    modifier: Modifier = Modifier,
    rowHeight: UiUnit = SelectableListDefaults.RowHeight,
    visibleRowCount: Int = SelectableListDefaults.VisibleRowCount,
    onSelectionChange: (Set<Int>) -> Unit,
) {
  val anchor = remember { intArrayOf(-1) }
  SelectableListHost(
      items = items,
      selectedIndices = selectedIndices,
      modifier = modifier,
      rowHeight = rowHeight,
      visibleRowCount = visibleRowCount,
      onItemClick = { index, modifiers ->
        onSelectionChange(
            resolveMultiSelection(selectedIndices, index, anchor[0], modifiers).also {
              if (!modifiers.shift) anchor[0] = index
            }
        )
      },
  )
}

internal fun resolveMultiSelection(
    current: Set<Int>,
    clicked: Int,
    anchor: Int,
    modifiers: KeyModifiers,
): Set<Int> =
    when {
      modifiers.shift && anchor >= 0 -> {
        val range = (minOf(anchor, clicked)..maxOf(anchor, clicked)).toSet()
        if (modifiers.ctrl) current + range else range
      }
      modifiers.ctrl -> if (clicked in current) current - clicked else current + clicked
      else -> setOf(clicked)
    }

@Composable
private fun SelectableListHost(
    items: List<String>,
    selectedIndices: Set<Int>,
    modifier: Modifier,
    rowHeight: UiUnit,
    visibleRowCount: Int,
    onItemClick: (index: Int, modifiers: KeyModifiers) -> Unit,
) {
  ComposeNode<SelectableListNode, NodeApplier>(
      factory = {
        SelectableListNode(
            modifier = modifier,
            items = items,
            selectedIndices = selectedIndices,
            rowHeight = rowHeight,
            visibleRowCount = visibleRowCount,
            onItemClick = onItemClick,
        )
      },
      update = {
        set(items) { this.items = it }
        set(selectedIndices) { this.selectedIndices = it }
        set(modifier) { this.modifier = it }
        set(rowHeight) { this.rowHeight = it }
        set(visibleRowCount) { this.visibleRowCount = it }
        set(onItemClick) { this.onItemClick = it }
      },
  )
}
