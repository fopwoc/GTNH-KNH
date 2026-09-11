package io.github.fopwoc.mods.framework.ui.compose.node

import androidx.compose.runtime.AbstractApplier

internal class NodeApplier(root: RootNode) : AbstractApplier<ComposeTreeNode>(root) {
  override fun insertTopDown(index: Int, instance: ComposeTreeNode) = Unit

  override fun insertBottomUp(index: Int, instance: ComposeTreeNode) {
    current.children.add(index, instance)
  }

  override fun remove(index: Int, count: Int) {
    repeat(count) {
      current.children.removeAt(index)
    }
  }

  override fun move(from: Int, to: Int, count: Int) {
    if (count <= 0 || from == to) {
      return
    }

    val moved = current.children.subList(from, from + count).toList()
    repeat(count) {
      current.children.removeAt(from)
    }
    val targetIndex = if (from < to) to - count else to
    current.children.addAll(targetIndex, moved)
  }

  override fun onClear() {
    root.children.clear()
  }
}
