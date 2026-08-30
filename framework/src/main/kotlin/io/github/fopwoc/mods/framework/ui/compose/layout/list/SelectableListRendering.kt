package io.github.fopwoc.mods.framework.ui.compose.layout.list

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.render.drawContainer
import io.github.fopwoc.mods.framework.ui.compose.layout.render.drawWithinClip
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.render.HostedElementRenderer

internal fun drawSelectableListElement(
    context: RenderContext,
    hostedElementRenderer: HostedElementRenderer,
    bounds: Rect,
    element: LayoutElement.SelectableList,
) {
  drawContainer(context, bounds, element.modifier)
  drawWithinClip(context, bounds) {
    hostedElementRenderer.drawSelectableList(bounds, element)
  }
}
