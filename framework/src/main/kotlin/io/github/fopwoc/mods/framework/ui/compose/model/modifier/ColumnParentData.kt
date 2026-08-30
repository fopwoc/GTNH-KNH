package io.github.fopwoc.mods.framework.ui.compose.model.modifier

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment

internal object ColumnParentDataKey : ParentDataKey<ColumnParentData>

internal data class ColumnParentData(
    val alignment: HorizontalAlignment? = null,
    val weight: Float? = null,
    val fill: Boolean = true,
)

internal fun Modifier.columnParentData(
    alignment: HorizontalAlignment? = null,
    weight: Float? = null,
    fill: Boolean = true,
): Modifier {
  var updated = this
  if (alignment != null) {
    updated = updated.withColumnParentData { it.copy(alignment = alignment) }
  }
  if (weight != null) {
    require(weight > 0f) { "Column weight must be greater than 0." }
    updated = updated.withColumnParentData { it.copy(weight = weight, fill = fill) }
  }
  return updated
}

internal fun Modifier.withColumnParentData(
    transform: (ColumnParentData) -> ColumnParentData
): Modifier =
    withParentData(
        key = ColumnParentDataKey,
        defaultValue = ::ColumnParentData,
        transform = transform,
    )

internal val Modifier.columnAlignment: HorizontalAlignment?
  get() = parentDataOrNull(ColumnParentDataKey)?.alignment

internal val Modifier.columnWeight: Float?
  get() = parentDataOrNull(ColumnParentDataKey)?.weight

internal val Modifier.columnFill: Boolean
  get() = parentDataOrNull(ColumnParentDataKey)?.fill ?: true
