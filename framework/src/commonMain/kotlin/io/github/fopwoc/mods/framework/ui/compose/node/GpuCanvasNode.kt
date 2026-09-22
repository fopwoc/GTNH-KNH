package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal class GpuCanvasNode(
    override var modifier: Modifier,
    var state: GpuCanvasState,
) : ComposeTreeNode(modifier) {
    val handle: Any = Any()
}
