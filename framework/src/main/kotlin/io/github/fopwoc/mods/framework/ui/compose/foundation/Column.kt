package io.github.fopwoc.mods.framework.ui.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode

@Composable
fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: VerticalArrangement = VerticalArrangement.Top,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.START,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    ComposeNode<ColumnNode, NodeApplier>(
        factory = {
            ColumnNode(
                modifier = modifier,
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment
            )
        },
        update = {
            set(modifier) { this.modifier = it }
            set(verticalArrangement) { this.verticalArrangement = it }
            set(horizontalAlignment) { this.horizontalAlignment = it }
        },
        content = {
            ColumnScopeInstance.content()
        }
    )
}


