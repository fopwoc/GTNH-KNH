package io.github.fopwoc.mods.framework.ui.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.RowNode
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

@Composable
fun Row(
    modifier: Modifier = Modifier(),
    spacing: UiUnit = UiUnit(0),
    verticalAlignment: VerticalAlignment = VerticalAlignment.TOP,
    content: @Composable () -> Unit = {}
) {
    ComposeNode<RowNode, NodeApplier>(
        factory = {
            RowNode(
                modifier = modifier,
                spacing = spacing,
                verticalAlignment = verticalAlignment
            )
        },
        update = {
            set(modifier) { this.modifier = it }
            set(spacing) { this.spacing = it }
            set(verticalAlignment) { this.verticalAlignment = it }
        },
        content = content
    )
}


