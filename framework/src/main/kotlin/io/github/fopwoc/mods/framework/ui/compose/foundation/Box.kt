package io.github.fopwoc.mods.framework.ui.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.BoxNode
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier

@Composable
fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit = {},
) {
  ComposeNode<BoxNode, NodeApplier>(
      factory = {
        BoxNode(
            modifier = modifier,
            contentAlignment = contentAlignment,
        )
      },
      update = {
        set(modifier) { this.modifier = it }
        set(contentAlignment) { this.contentAlignment = it }
      },
      content = {
        BoxScopeInstance.content()
      },
  )
}
