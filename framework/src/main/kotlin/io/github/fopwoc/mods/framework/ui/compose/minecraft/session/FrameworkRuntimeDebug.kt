package io.github.fopwoc.mods.framework.ui.compose.minecraft.session

import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutNode
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.node.ButtonNode
import io.github.fopwoc.mods.framework.ui.compose.node.ComposeTreeNode
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextNode

object FrameworkRuntimeDebug {
  const val SENTINEL: String = "knh-core:render-refresh-sentinel-2026-05-06b"

  @Volatile
  var runtimeStatus: String = "unseen"
    private set

  @Volatile
  var dispatcherStatus: String = "unseen"
    private set

  @Volatile
  var runtimeFailure: String = "none"
    private set

  @Volatile
  var lastRenderEpoch: Int = -1
    private set

  @Volatile
  var lastRootSnapshot: String = "unseen"
    private set

  @Volatile
  var lastLayoutSnapshot: String = "unseen"
    private set

  internal fun captureRenderTree(renderEpoch: Int, rootNode: RootNode, layoutRoot: LayoutNode) {
    lastRenderEpoch = renderEpoch
    lastRootSnapshot = summarizeComposeTree(rootNode)
    lastLayoutSnapshot = summarizeLayoutTree(layoutRoot)
  }

  internal fun resetRenderTree() {
    lastRenderEpoch = -1
    lastRootSnapshot = "unseen"
    lastLayoutSnapshot = "unseen"
    runtimeStatus = "unseen"
    dispatcherStatus = "unseen"
    runtimeFailure = "none"
  }

  internal fun updateRuntimeStatus(status: String) {
    runtimeStatus = status
  }

  internal fun updateDispatcherStatus(status: String) {
    dispatcherStatus = status
  }

  internal fun updateRuntimeFailure(failure: String) {
    val current = runtimeFailure
    runtimeFailure =
        when {
          failure == current -> current
          failure == "none" && current != "none" -> current
          current == "none" -> failure
          current.startsWith(
              "recomposeJob:CancellationException:Recomposer effect job completed"
          ) -> failure
          failure.startsWith(
              "recomposeJob:CancellationException:Recomposer effect job completed"
          ) -> current
          else -> failure
        }
  }

  private fun summarizeComposeTree(rootNode: RootNode): String {
    val lines = mutableListOf<String>()
    collectComposeLines(rootNode, lines)
    return lines.toDebugSummary()
  }

  private fun summarizeLayoutTree(rootNode: LayoutNode): String {
    val lines = mutableListOf<String>()
    collectLayoutLines(rootNode, lines)
    return lines.toDebugSummary()
  }

  private fun collectComposeLines(node: ComposeTreeNode, lines: MutableList<String>) {
    when (node) {
      is TextNode -> lines += node.text.plainText
      is ButtonNode -> lines += "[button] ${node.text.plainText}"
      else -> Unit
    }
    node.children.forEach { child ->
      collectComposeLines(child, lines)
    }
  }

  private fun collectLayoutLines(node: LayoutNode, lines: MutableList<String>) {
    when (val element = node.element) {
      is LayoutElement.Text -> lines += element.text.plainText
      is LayoutElement.Button -> lines += "[button] ${element.text.plainText}"
      else -> Unit
    }
    node.children.forEach { child ->
      collectLayoutLines(child, lines)
    }
  }

  private fun List<String>.toDebugSummary(): String {
    val interesting = filter { line ->
      line.contains("state=") ||
          line.contains("vmToken=") ||
          line.contains("callback") ||
          line.contains("pressed") ||
          line.startsWith("[button]")
    }
    val selected = (interesting.ifEmpty { this }).take(8)
    return if (selected.isEmpty()) {
      "empty"
    } else {
      selected.joinToString(separator = " | ")
    }
  }
}
