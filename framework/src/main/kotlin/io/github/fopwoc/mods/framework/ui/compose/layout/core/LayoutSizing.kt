package io.github.fopwoc.mods.framework.ui.compose.layout.core

import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.resolvedFixedHeight
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.resolvedFixedWidth

internal fun availableInnerWidth(modifier: Modifier, maxWidth: Int): Int {
  val fixedWidth = modifier.resolvedFixedWidth
  val containerWidth =
      when {
        fixedWidth != null -> fixedWidth.coerceAtMost(maxWidth)
        modifier.fillMaxWidth -> maxWidth
        else -> maxWidth
      }
  return (containerWidth - modifier.padding.horizontalValue).coerceAtLeast(0)
}

internal fun availableInnerHeight(modifier: Modifier, maxHeight: Int): Int {
  val fixedHeight = modifier.resolvedFixedHeight
  val containerHeight =
      when {
        fixedHeight != null -> fixedHeight.coerceAtMost(maxHeight)
        modifier.fillMaxHeight -> maxHeight
        else -> maxHeight
      }
  return (containerHeight - modifier.padding.verticalValue).coerceAtLeast(0)
}

internal fun resolveSize(
    modifier: Modifier,
    naturalWidth: Int,
    naturalHeight: Int,
    maxWidth: Int,
    maxHeight: Int,
): Size {
  return Size(
      width = resolveWidth(modifier, naturalWidth, maxWidth),
      height = resolveHeight(modifier, naturalHeight, maxHeight),
  )
}

internal fun resolveWidth(modifier: Modifier, naturalWidth: Int, maxWidth: Int): Int {
  val fixedWidth = modifier.resolvedFixedWidth
  return when {
    fixedWidth != null -> fixedWidth.coerceAtMost(maxWidth)
    modifier.fillMaxWidth -> maxWidth
    else -> naturalWidth.coerceAtMost(maxWidth)
  }.coerceAtLeast(0)
}

internal fun resolveHeight(modifier: Modifier, naturalHeight: Int, maxHeight: Int): Int {
  val fixedHeight = modifier.resolvedFixedHeight
  return when {
    fixedHeight != null -> fixedHeight.coerceAtMost(maxHeight)
    modifier.fillMaxHeight -> maxHeight
    else -> naturalHeight.coerceAtMost(maxHeight)
  }.coerceAtLeast(0)
}
