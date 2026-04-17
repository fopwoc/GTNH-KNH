package io.github.fopwoc.mods.framework.ui.compose.component.native

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.SelectableListNode

@Composable
fun SelectableList(
    items: List<String>,
    selectedIndex: Int = -1,
    modifier: Modifier = Modifier(),
    rowHeight: Int = 18,
    visibleRowCount: Int = 6,
    onSelectedIndexChange: (Int) -> Unit
) {
    ComposeNode<SelectableListNode, NodeApplier>(
        factory = {
            SelectableListNode(
                modifier = modifier,
                items = items,
                selectedIndex = selectedIndex,
                rowHeight = rowHeight,
                visibleRowCount = visibleRowCount,
                onSelectedIndexChange = onSelectedIndexChange
            )
        },
        update = {
            set(items) { this.items = it }
            set(selectedIndex) { this.selectedIndex = it }
            set(modifier) { this.modifier = it }
            set(rowHeight) { this.rowHeight = it }
            set(visibleRowCount) { this.visibleRowCount = it }
            set(onSelectedIndexChange) { this.onSelectedIndexChange = it }
        }
    )
}



