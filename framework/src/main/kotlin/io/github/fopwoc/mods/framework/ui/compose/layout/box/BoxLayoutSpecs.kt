package io.github.fopwoc.mods.framework.ui.compose.layout.box

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.core.availableInnerHeight
import io.github.fopwoc.mods.framework.ui.compose.layout.core.availableInnerWidth
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement

internal fun LayoutElement.Box.boxMeasureSpec(maxWidth: Int, maxHeight: Int): BoxMeasureSpec =
    BoxMeasureSpec(
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        innerWidth = availableInnerWidth(modifier, maxWidth),
        innerHeight = availableInnerHeight(modifier, maxHeight),
    )

internal fun LayoutElement.Box.boxPlacementSpec(contentRect: Rect): BoxPlacementSpec =
    BoxPlacementSpec(
        contentRect = contentRect,
        contentAlignment = contentAlignment,
    )
