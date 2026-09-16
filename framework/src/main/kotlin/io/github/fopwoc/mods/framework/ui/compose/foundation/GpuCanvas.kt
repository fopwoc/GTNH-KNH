package io.github.fopwoc.mods.framework.ui.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.GpuCanvasNode
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier

/** Reserves Compose layout space and draws a prepared frame of pixel images on the GPU. */
@Composable
fun GpuCanvas(
    frame: GpuCanvasFrame,
    modifier: Modifier = Modifier,
) {
  ComposeNode<GpuCanvasNode, NodeApplier>(
      factory = { GpuCanvasNode(modifier, frame) },
      update = {
        set(modifier) { this.modifier = it }
        set(frame) { this.frame = it }
      },
  )
}
