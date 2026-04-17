package io.github.fopwoc.mods.framework.ui.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.BoxNode

@Composable
fun Box(
    modifier: Modifier = Modifier(),
    content: @Composable () -> Unit = {}
) {
    ComposeNode<BoxNode, NodeApplier>(
        factory = { BoxNode(modifier = modifier) },
        update = {
            set(modifier) { this.modifier = it }
        },
        content = content
    )
}


