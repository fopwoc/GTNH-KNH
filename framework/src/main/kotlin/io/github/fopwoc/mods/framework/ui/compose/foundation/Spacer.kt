package io.github.fopwoc.mods.framework.ui.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.SpacerNode
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

@Composable
fun Spacer(
    width: UiUnit = UiUnit(0),
    height: UiUnit = UiUnit(0),
    modifier: Modifier = Modifier,
) {
  val resolvedModifier = modifier.width(width).height(height)
  ComposeNode<SpacerNode, NodeApplier>(
      factory = { SpacerNode(modifier = resolvedModifier) },
      update = {
        set(resolvedModifier) { this.modifier = it }
      },
  )
}
