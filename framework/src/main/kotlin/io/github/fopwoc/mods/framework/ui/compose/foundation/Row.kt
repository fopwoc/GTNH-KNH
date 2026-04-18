package io.github.fopwoc.mods.framework.ui.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.RowNode

@Composable
fun Row(
    modifier: Modifier = Modifier(),
    horizontalArrangement: HorizontalArrangement = HorizontalArrangement.Start,
    verticalAlignment: VerticalAlignment = VerticalAlignment.TOP,
    content: @Composable () -> Unit = {}
) {
    ComposeNode<RowNode, NodeApplier>(
        factory = {
            RowNode(
                modifier = modifier,
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = verticalAlignment
            )
        },
        update = {
            set(modifier) { this.modifier = it }
            set(horizontalArrangement) { this.horizontalArrangement = it }
            set(verticalAlignment) { this.verticalAlignment = it }
        },
        content = content
    )
}


