package io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted

import cpw.mods.fml.client.config.GuiButtonExt
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey

internal fun drawMinecraftHostedButton(
    registry: MinecraftHostedWidgetRegistry,
    environment: MinecraftHostedWidgetRenderEnvironment,
    bounds: Rect,
    hostKey: HostedWidgetKey,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (!bounds.hasVisibleHostedBounds()) {
        return
    }

    val hosted = registry.getOrCreateButton(hostKey) {
        HostedButton(
            widget = GuiButtonExt(0, bounds.x, bounds.y, bounds.width, bounds.height, text),
            onClick = onClick
        )
    }

    hosted.markSeen(environment.renderEpoch)
    hosted.onClick = onClick
    updateButtonWidget(
        widget = hosted.widget,
        bounds = bounds,
        text = text,
        enabled = enabled
    )
    hosted.widget.drawButton(environment.client, environment.mouseX, environment.mouseY)
    environment.registerInputTarget(
        capturedPressInputTarget(
            kind = InputTargetKind.BUTTON,
            bounds = bounds,
            onPressAttempt = { clickX, clickY, _ ->
                hosted.widget.mousePressed(environment.client, clickX, clickY)
            },
            validityCheck = { registry.ownsButton(hostKey, hosted) },
            onCaptured = hosted.onClick,
            onRelease = { releaseX, releaseY, releaseButton, pressedButton ->
                hosted.widget.mouseReleased(releaseX, releaseY)
                releaseButton == pressedButton
            }
        )
    )
}

private fun updateButtonWidget(widget: GuiButtonExt, bounds: Rect, text: String, enabled: Boolean) {
    widget.xPosition = bounds.x
    widget.yPosition = bounds.y
    widget.width = bounds.width
    widget.height = bounds.height
    widget.displayString = text
    widget.enabled = enabled
    widget.visible = true
}



