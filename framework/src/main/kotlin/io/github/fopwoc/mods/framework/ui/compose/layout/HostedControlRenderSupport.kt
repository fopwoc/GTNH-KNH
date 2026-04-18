package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement

internal fun drawHostedButton(
    context: RenderContext,
    bounds: Rect,
    element: LayoutElement.Button
) {
    context.drawVanillaButton(
        bounds = bounds,
        hostKey = element.hostKey,
        text = element.text,
        enabled = element.enabled,
        onClick = element.onClick
    )
}

internal fun drawHostedCheckbox(
    context: RenderContext,
    bounds: Rect,
    element: LayoutElement.Checkbox
) {
    context.drawVanillaCheckbox(
        bounds = bounds,
        hostKey = element.hostKey,
        label = element.label,
        checked = element.checked,
        enabled = element.enabled,
        onCheckedChange = element.onCheckedChange
    )
}

internal fun drawHostedTextField(
    context: RenderContext,
    bounds: Rect,
    element: LayoutElement.TextField
) {
    context.drawVanillaTextField(
        bounds = bounds,
        hostKey = element.hostKey,
        state = element.state,
        placeholder = element.placeholder,
        enabled = element.enabled,
        style = element.style
    )
}

internal fun drawHostedSlider(
    context: RenderContext,
    bounds: Rect,
    element: LayoutElement.Slider
) {
    context.drawVanillaSlider(
        bounds = bounds,
        hostKey = element.hostKey,
        value = element.value,
        valueRangeStart = element.valueRangeStart,
        valueRangeEnd = element.valueRangeEnd,
        label = element.label,
        suffix = element.suffix,
        enabled = element.enabled,
        showDecimal = element.showDecimal,
        onValueChange = element.onValueChange
    )
}

