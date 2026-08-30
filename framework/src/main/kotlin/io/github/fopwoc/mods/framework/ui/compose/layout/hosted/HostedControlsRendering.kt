package io.github.fopwoc.mods.framework.ui.compose.layout.hosted

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.render.HostedElementRenderer

internal fun drawHostedButton(
    context: RenderContext,
    hostedElementRenderer: HostedElementRenderer,
    bounds: Rect,
    element: LayoutElement.Button,
) {
  hostedElementRenderer.drawButton(bounds, element)
}

internal fun drawHostedCheckbox(
    context: RenderContext,
    hostedElementRenderer: HostedElementRenderer,
    bounds: Rect,
    element: LayoutElement.Checkbox,
) {
  hostedElementRenderer.drawCheckbox(bounds, element)
}

internal fun drawHostedTextField(
    context: RenderContext,
    hostedElementRenderer: HostedElementRenderer,
    bounds: Rect,
    element: LayoutElement.TextField,
) {
  hostedElementRenderer.drawTextField(bounds, element)
}

internal fun drawHostedSlider(
    context: RenderContext,
    hostedElementRenderer: HostedElementRenderer,
    bounds: Rect,
    element: LayoutElement.Slider,
) {
  hostedElementRenderer.drawSlider(bounds, element)
}
