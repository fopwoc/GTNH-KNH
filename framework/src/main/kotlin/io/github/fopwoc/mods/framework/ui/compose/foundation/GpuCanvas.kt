package io.github.fopwoc.mods.framework.ui.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.remember
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.GpuCanvasNode
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier

/** Reserves Compose layout space and draws a prepared frame of pixel images on the GPU. */
@Composable
fun GpuCanvas(
    frame: GpuCanvasFrame,
    modifier: Modifier = Modifier,
) {
  val state = remember { GpuCanvasState(frame) }
  state.submit(frame)
  GpuCanvas(state, modifier)
}

/** Reserves Compose layout space while [state] supplies frames directly to the render path. */
@Composable
fun GpuCanvas(
    state: GpuCanvasState,
    modifier: Modifier = Modifier,
) {
  ComposeNode<GpuCanvasNode, NodeApplier>(
      factory = { GpuCanvasNode(modifier, state) },
      update = {
        set(modifier) { this.modifier = it }
        set(state) { this.state = it }
      },
  )
}
