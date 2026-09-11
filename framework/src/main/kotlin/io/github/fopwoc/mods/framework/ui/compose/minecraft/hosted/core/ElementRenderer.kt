package io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.minecraft.render.MinecraftRenderFrameContext
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.render.HostedElementRenderer

internal class MinecraftHostedElementRenderer(
    private val frame: MinecraftRenderFrameContext,
    private val hostedWidgets: MinecraftHostedWidgetRegistry,
    private val registerInputTarget: (InputTarget) -> Unit,
) : HostedElementRenderer {
  override fun drawButton(bounds: Rect, element: LayoutElement.Button) {
    drawMinecraftHostedButton(
        registry = hostedWidgets,
        environment = hostedWidgetEnvironment(),
        bounds = bounds,
        hostKey = element.hostKey,
        text = element.text.formattedString,
        enabled = element.enabled,
        onClick = element.onClick,
    )
  }

  override fun drawCheckbox(bounds: Rect, element: LayoutElement.Checkbox) {
    drawMinecraftHostedCheckbox(
        registry = hostedWidgets,
        environment = hostedWidgetEnvironment(),
        bounds = bounds,
        hostKey = element.hostKey,
        label = element.label.formattedString,
        checked = element.checked,
        enabled = element.enabled,
        onCheckedChange = element.onCheckedChange,
    )
  }

  override fun drawSlider(bounds: Rect, element: LayoutElement.Slider) {
    drawMinecraftHostedSlider(
        registry = hostedWidgets,
        environment = hostedWidgetEnvironment(),
        bounds = bounds,
        hostKey = element.hostKey,
        value = element.value,
        valueRangeStart = element.valueRangeStart,
        valueRangeEnd = element.valueRangeEnd,
        label = element.label,
        suffix = element.suffix,
        enabled = element.enabled,
        showDecimal = element.showDecimal,
        onValueChange = element.onValueChange,
    )
  }

  private fun hostedWidgetEnvironment(): MinecraftHostedWidgetRenderEnvironment {
    return MinecraftHostedWidgetRenderEnvironment(
        frame = frame,
        registerInputTarget = registerInputTarget,
    )
  }
}
