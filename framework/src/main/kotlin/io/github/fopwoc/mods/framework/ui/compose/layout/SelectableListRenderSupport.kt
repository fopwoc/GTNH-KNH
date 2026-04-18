package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved

internal fun drawSelectableListElement(
    context: RenderContext,
    bounds: Rect,
    element: LayoutElement.SelectableList
) {
    drawContainer(context, bounds, element.modifier)
    drawWithinClip(context, bounds) {
        context.drawVanillaSelectableList(
            bounds = bounds,
            hostKey = element.hostKey,
            items = element.items,
            selectedIndex = element.selectedIndex,
            rowHeight = element.rowHeight.resolved,
            onSelectedIndexChange = element.onSelectedIndexChange
        )
    }
}

