package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import kotlin.math.max

internal fun resolveScrollMetrics(
    bounds: Rect,
    modifier: Modifier,
    contentHeight: Int,
    state: ScrollState
): ScrollMetrics {
    val scrollArea = bounds.inset(modifier.padding)
    val gutterWidth = if (contentHeight > scrollArea.height) {
        ScrollbarGutterWidth.coerceAtMost(scrollArea.width)
    } else {
        0
    }
    val viewportBounds = Rect(
        x = scrollArea.x,
        y = scrollArea.y,
        width = (scrollArea.width - gutterWidth).coerceAtLeast(0),
        height = scrollArea.height
    )
    val trackBounds = resolveScrollTrackBounds(scrollArea, viewportBounds, gutterWidth)
    state.updateMaxValue(contentHeight - viewportBounds.height)
    return ScrollMetrics(
        scrollArea = scrollArea,
        viewportBounds = viewportBounds,
        trackBounds = trackBounds,
        contentHeight = contentHeight,
        state = state
    )
}

internal fun resolveScrollThumbBounds(metrics: ScrollMetrics): Rect? {
    val trackBounds = metrics.trackBounds ?: return null
    if (metrics.maxValue <= 0 || metrics.viewportBounds.width <= 0 || metrics.viewportBounds.height <= 0) {
        return null
    }

    val thumbHeight = max(16, metrics.viewportBounds.height * metrics.viewportBounds.height / metrics.contentHeight.coerceAtLeast(1))
        .coerceAtMost(metrics.viewportBounds.height)
    val thumbTravel = (trackBounds.height - thumbHeight).coerceAtLeast(0)
    val thumbTop = trackBounds.y + if (metrics.maxValue == 0) {
        0
    } else {
        thumbTravel * metrics.state.value / metrics.maxValue
    }

    return Rect(
        x = trackBounds.x,
        y = thumbTop,
        width = trackBounds.width,
        height = thumbHeight
    )
}

private fun resolveScrollTrackBounds(
    scrollArea: Rect,
    viewportBounds: Rect,
    gutterWidth: Int
): Rect? {
    if (gutterWidth <= 0 || scrollArea.height <= 0) {
        return null
    }

    return Rect(
        x = viewportBounds.x + viewportBounds.width + ScrollbarTrackInset,
        y = scrollArea.y,
        width = ScrollbarTrackWidth.coerceAtMost(gutterWidth),
        height = scrollArea.height
    )
}

