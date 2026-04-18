package io.github.fopwoc.mods.framework.ui.compose.foundation

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.rowParentData

@LayoutScopeMarker
interface RowScope {
    fun Modifier.align(alignment: VerticalAlignment): Modifier

    fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier
}

internal object RowScopeInstance : RowScope {
    override fun Modifier.align(alignment: VerticalAlignment): Modifier = rowParentData(alignment = alignment)

    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = rowParentData(weight = weight, fill = fill)
}

