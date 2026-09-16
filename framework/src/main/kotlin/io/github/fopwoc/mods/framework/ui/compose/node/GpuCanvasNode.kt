package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal class GpuCanvasNode(
    override var modifier: Modifier,
    var frame: GpuCanvasFrame,
) : ComposeTreeNode(modifier) {
  val handle: Any = Any()
}
