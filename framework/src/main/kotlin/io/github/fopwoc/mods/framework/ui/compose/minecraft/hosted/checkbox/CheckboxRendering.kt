package io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted

import cpw.mods.fml.client.config.GuiCheckBox
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey

internal fun drawMinecraftHostedCheckbox(
    registry: MinecraftHostedWidgetRegistry,
    environment: MinecraftHostedWidgetRenderEnvironment,
    bounds: Rect,
    hostKey: HostedWidgetKey,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    if (!bounds.hasVisibleHostedBounds()) {
        return
    }

    val hosted = registry.getOrCreateCheckbox(hostKey) {
        HostedCheckbox(
            widget = GuiCheckBox(0, bounds.x, bounds.y, label, checked),
            onCheckedChange = onCheckedChange
        )
    }

    hosted.markSeen(environment.renderEpoch)
    hosted.onCheckedChange = onCheckedChange
    updateCheckboxWidget(
        widget = hosted.widget,
        bounds = bounds,
        label = label,
        enabled = enabled
    )
    if (hosted.widget.isChecked() != checked) {
        hosted.widget.setIsChecked(checked)
    }
    hosted.widget.drawButton(environment.client, environment.mouseX, environment.mouseY)
    environment.registerInputTarget(
        capturedPressInputTarget(
            kind = InputTargetKind.CHECKBOX,
            bounds = bounds,
            onPressAttempt = { clickX, clickY, _ ->
                hosted.widget.mousePressed(environment.client, clickX, clickY)
            },
            validityCheck = { registry.ownsCheckbox(hostKey, hosted) },
            onCaptured = { hosted.onCheckedChange(hosted.widget.isChecked()) },
            onRelease = { releaseX, releaseY, releaseButton, pressedButton ->
                hosted.widget.mouseReleased(releaseX, releaseY)
                releaseButton == pressedButton
            }
        )
    )
}

private fun updateCheckboxWidget(widget: GuiCheckBox, bounds: Rect, label: String, enabled: Boolean) {
    widget.xPosition = bounds.x
    widget.yPosition = bounds.y
    widget.width = bounds.width
    widget.height = bounds.height.coerceAtLeast(11)
    widget.displayString = label
    widget.enabled = enabled
    widget.visible = true
}



