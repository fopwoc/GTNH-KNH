package io.github.fopwoc.mods.framework.ui.compose.model.modifier

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment

internal object BoxParentDataKey : ParentDataKey<BoxParentData>

internal data class BoxParentData(
    val alignment: Alignment? = null,
    val matchParentWidth: Boolean = false,
    val matchParentHeight: Boolean = false,
) {
  val matchParentSize: Boolean
    get() = matchParentWidth && matchParentHeight
}

internal fun Modifier.boxParentData(
    alignment: Alignment? = null,
    matchParentWidth: Boolean = false,
    matchParentHeight: Boolean = false,
    matchParentSize: Boolean = false,
): Modifier {
  var updated = this
  if (alignment != null) {
    updated = updated.withBoxParentData { it.copy(alignment = alignment) }
  }
  if (matchParentWidth || matchParentSize) {
    updated = updated.withBoxParentData { it.copy(matchParentWidth = true) }
  }
  if (matchParentHeight || matchParentSize) {
    updated = updated.withBoxParentData { it.copy(matchParentHeight = true) }
  }
  return updated
}

internal fun Modifier.withBoxParentData(transform: (BoxParentData) -> BoxParentData): Modifier =
    withParentData(
        key = BoxParentDataKey,
        defaultValue = ::BoxParentData,
        transform = transform,
    )

internal val Modifier.boxAlignment: Alignment?
  get() = parentDataOrNull(BoxParentDataKey)?.alignment

internal val Modifier.boxMatchesParentWidth: Boolean
  get() = parentDataOrNull(BoxParentDataKey)?.matchParentWidth == true

internal val Modifier.boxMatchesParentHeight: Boolean
  get() = parentDataOrNull(BoxParentDataKey)?.matchParentHeight == true

internal val Modifier.boxMatchesParentSize: Boolean
  get() = parentDataOrNull(BoxParentDataKey)?.matchParentSize == true
