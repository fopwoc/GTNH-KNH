package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutProjection

internal fun ComposeTreeNode.toLayoutProjection(): LayoutProjection {
  toContainerProjectionOrNull()?.let { projection ->
    return projection
  }
  toLeafProjectionOrNull()?.let { projection ->
    return projection
  }

  error("Unsupported compose node projection: ${this::class.simpleName}")
}
