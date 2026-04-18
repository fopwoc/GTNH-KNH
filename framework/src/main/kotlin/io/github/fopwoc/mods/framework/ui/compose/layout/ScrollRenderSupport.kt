package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement

internal fun drawScrollableColumnElement(
    context: RenderContext,
    bounds: Rect,
    element: LayoutElement.ScrollableColumn,
    metrics: ScrollMetrics?,
    drawChildren: () -> Unit
) {
    drawContainer(context, bounds, element.modifier)
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

    context.fillRect(trackBounds.x, trackBounds.y, trackBounds.x + trackBounds.width, trackBounds.y + trackBounds.height, 0x5535353F)
    context.fillRect(thumbBounds.x, thumbBounds.y, thumbBounds.x + thumbBounds.width, thumbBounds.y + thumbBounds.height, 0xCCB8B8C4.toInt())
}

