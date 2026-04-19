package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal fun drawScrollableStackElement(
    context: RenderContext,
    bounds: Rect,
    modifier: Modifier,
    metrics: ScrollMetrics?,
    drawChildren: () -> Unit
) {
    drawContainer(context, bounds, modifier)
    val resolvedMetrics = metrics ?: return
    registerScrollWheelTarget(context, resolvedMetrics)
    drawWithinClip(context, resolvedMetrics.viewportBounds, drawChildren)
    drawScrollIndicator(context, resolvedMetrics)
    registerScrollThumbTarget(context, resolvedMetrics)
}

internal fun drawScrollIndicator(context: RenderContext, metrics: ScrollMetrics) {
    val trackBounds = metrics.trackBounds ?: return
    val thumbBounds = resolveScrollThumbBounds(metrics) ?: return
    if (metrics.viewportBounds.width <= 0 || metrics.viewportBounds.height <= 0) {
        return
    }

    context.fillRect(trackBounds.x, trackBounds.y, trackBounds.x + trackBounds.width, trackBounds.y + trackBounds.height, Color(0x5535353F))
    context.fillRect(thumbBounds.x, thumbBounds.y, thumbBounds.x + thumbBounds.width, thumbBounds.y + thumbBounds.height, Color(0xCCB8B8C4))
}

