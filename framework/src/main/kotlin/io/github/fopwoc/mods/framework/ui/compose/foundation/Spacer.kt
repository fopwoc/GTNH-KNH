package io.github.fopwoc.mods.framework.ui.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.SpacerNode

@Composable
fun Spacer(
    width: Int = 0,
    height: Int = 0,
    modifier: Modifier = Modifier()
) {
    val resolvedModifier = modifier.width(width).height(height)
    ComposeNode<SpacerNode, NodeApplier>(
        factory = { SpacerNode(modifier = resolvedModifier) },
        update = {
            set(resolvedModifier) { this.modifier = it }
        }
    )
}


