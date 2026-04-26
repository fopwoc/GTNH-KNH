package io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted

import cpw.mods.fml.client.config.GuiSlider
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import kotlin.math.abs

internal fun drawMinecraftHostedSlider(
    registry: MinecraftHostedWidgetRegistry,
    environment: MinecraftHostedWidgetRenderEnvironment,
    bounds: Rect,
    hostKey: HostedWidgetKey,
    value: Double,
    valueRangeStart: Double,
    valueRangeEnd: Double,
    label: String,
    suffix: String,
    enabled: Boolean,
    showDecimal: Boolean,
    onValueChange: (Double) -> Unit
) {
    if (!bounds.hasVisibleHostedBounds()) {
        return
    }

    val start = minOf(valueRangeStart, valueRangeEnd)
    val end = maxOf(valueRangeStart, valueRangeEnd)
    val coercedValue = value.coerceIn(start, end)
    val prefix = sliderPrefix(label)
    val hosted = registry.getSlider(hostKey)
        ?.takeUnless {
            it.label != label ||
                it.suffix != suffix ||
                it.valueRangeStart != start ||
                it.valueRangeEnd != end ||
                it.showDecimal != showDecimal
        }
        ?: createHostedSlider(
            registry = registry,
            hostKey = hostKey,
            bounds = bounds,
            prefix = prefix,
            label = label,
            suffix = suffix,
            valueRangeStart = start,
            valueRangeEnd = end,
            value = coercedValue,
            showDecimal = showDecimal,
            onValueChange = onValueChange
        )

    hosted.markSeen(environment.renderEpoch)
    hosted.onValueChange = onValueChange
    updateSliderWidget(
        widget = hosted.widget,
        bounds = bounds,
        prefix = prefix,
        suffix = suffix,
        enabled = enabled,
        showDecimal = showDecimal
    )
    updateSliderValue(hosted, coercedValue)
    hosted.widget.drawButton(environment.client, environment.mouseX, environment.mouseY)
    environment.registerInputTarget(
        capturedPressInputTarget(
            kind = InputTargetKind.SLIDER,
            bounds = bounds,
            onPressAttempt = { clickX, clickY, _ ->
                hosted.widget.mousePressed(environment.client, clickX, clickY)
            },
            validityCheck = { registry.ownsSlider(hostKey, hosted) },
            onRelease = { releaseX, releaseY, releaseButton, pressedButton ->
                hosted.widget.mouseReleased(releaseX, releaseY)
                releaseButton == pressedButton
            }
        )
    )
}

private fun createHostedSlider(
    registry: MinecraftHostedWidgetRegistry,
    hostKey: HostedWidgetKey,
    bounds: Rect,
    prefix: String,
    label: String,
    suffix: String,
    valueRangeStart: Double,
    valueRangeEnd: Double,
    value: Double,
    showDecimal: Boolean,
    onValueChange: (Double) -> Unit
): HostedSlider {
    lateinit var hosted: HostedSlider
    val widget = GuiSlider(
        0,
        bounds.x,
        bounds.y,
        bounds.width,
        bounds.height,
        prefix,
        suffix,
        valueRangeStart,
        valueRangeEnd,
        value,
        showDecimal,
        true,
        { slider ->
            if (!hosted.suppressCallback) {
                hosted.onValueChange(slider.getValue())
            }
        }
    )
    hosted = HostedSlider(
        widget = widget,
        label = label,
        suffix = suffix,
        valueRangeStart = valueRangeStart,
        valueRangeEnd = valueRangeEnd,
        showDecimal = showDecimal,
        onValueChange = onValueChange
    )
    registry.putSlider(hostKey, hosted)
    return hosted
}

private fun updateSliderWidget(
    widget: GuiSlider,
    bounds: Rect,
    prefix: String,
    suffix: String,
    enabled: Boolean,
    showDecimal: Boolean
) {
    widget.xPosition = bounds.x
    widget.yPosition = bounds.y
    widget.width = bounds.width
    widget.height = bounds.height
    widget.enabled = enabled
    widget.visible = true
    widget.dispString = prefix
    widget.suffix = suffix
    widget.showDecimal = showDecimal
}

private fun updateSliderValue(hosted: HostedSlider, coercedValue: Double) {
    if (abs(hosted.widget.getValue() - coercedValue) > 1e-9) {
        hosted.suppressCallback = true
        hosted.widget.setValue(coercedValue)
        hosted.widget.updateSlider()
        hosted.suppressCallback = false
    }
}


private fun sliderPrefix(label: String): String {
    return if (label.isBlank()) {
        ""
    } else {
        "$label: "
    }
}


