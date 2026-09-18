package io.github.fopwoc.mods.framework.ui.compose.layout.box

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment

internal data class BoxPlacementSpec(
    val contentRect: Rect,
    val contentAlignment: Alignment,
)
