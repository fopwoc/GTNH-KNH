package io.github.fopwoc.mods.framework.ui.compose.model.modifier

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment

internal object RowParentDataKey : ParentDataKey<RowParentData>

internal data class RowParentData(
    val alignment: VerticalAlignment? = null,
    val weight: Float? = null,
    val fill: Boolean = true,
)

internal fun Modifier.rowParentData(
    alignment: VerticalAlignment? = null,
    weight: Float? = null,
    fill: Boolean = true,
): Modifier {
  var updated = this
  if (alignment != null) {
    updated = updated.withRowParentData { it.copy(alignment = alignment) }
  }
  if (weight != null) {
    require(weight > 0f) { "Row weight must be greater than 0." }
    updated = updated.withRowParentData { it.copy(weight = weight, fill = fill) }
  }
  return updated
}

internal fun Modifier.withRowParentData(transform: (RowParentData) -> RowParentData): Modifier =
    withParentData(
        key = RowParentDataKey,
        defaultValue = ::RowParentData,
        transform = transform,
    )

internal val Modifier.rowAlignment: VerticalAlignment?
  get() = parentDataOrNull(RowParentDataKey)?.alignment

internal val Modifier.rowWeight: Float?
  get() = parentDataOrNull(RowParentDataKey)?.weight

internal val Modifier.rowFill: Boolean
  get() = parentDataOrNull(RowParentDataKey)?.fill ?: true
