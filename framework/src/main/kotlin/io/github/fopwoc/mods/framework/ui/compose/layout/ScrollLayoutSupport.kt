package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import kotlin.math.max

internal fun resolveScrollMetrics(
    bounds: Rect,
    modifier: Modifier,
    contentMainAxisSize: Int,
    state: ScrollState,
    axis: StackAxis
): ScrollMetrics {
    // Layout measurement remains pure and depends only on geometry inputs. The mutable
    // ScrollState is consulted here at placement time so the returned metrics can carry
    // current viewport offsets and input targets for this frame without threading state
    // reads back into measurement.
    val scrollArea = bounds.inset(modifier.padding)
    val gutterSize = if (contentMainAxisSize > scrollArea.mainAxisSize(axis)) {
        ScrollbarGutterWidth.coerceAtMost(scrollArea.crossAxisSize(axis))
    } else {
        0
    }
    val viewportBounds = when (axis) {
        StackAxis.VERTICAL -> Rect(
            x = scrollArea.x,
            y = scrollArea.y,
            width = (scrollArea.width - gutterSize).coerceAtLeast(0),
            height = scrollArea.height
        )
        StackAxis.HORIZONTAL -> Rect(
            x = scrollArea.x,
            y = scrollArea.y,
            width = scrollArea.width,
            height = (scrollArea.height - gutterSize).coerceAtLeast(0)
        )
    }
    val trackBounds = resolveScrollTrackBounds(scrollArea, viewportBounds, gutterSize, axis)
    state.updateMaxValue(contentMainAxisSize - viewportBounds.mainAxisSize(axis))
    return ScrollMetrics(
        axis = axis,
        scrollArea = scrollArea,
        viewportBounds = viewportBounds,
        trackBounds = trackBounds,
        contentMainAxisSize = contentMainAxisSize,
        state = state
    )
}

internal fun resolveScrollThumbBounds(metrics: ScrollMetrics): Rect? {
    val trackBounds = metrics.trackBounds ?: return null
    if (metrics.maxValue <= 0 || metrics.viewportBounds.width <= 0 || metrics.viewportBounds.height <= 0) {
        return null
    }

    val viewportMainAxisSize = metrics.viewportBounds.mainAxisSize(metrics.axis)
    val thumbLength = max(16, viewportMainAxisSize * viewportMainAxisSize / metrics.contentMainAxisSize.coerceAtLeast(1))
        .coerceAtMost(viewportMainAxisSize)
    val thumbTravel = (trackBounds.mainAxisSize(metrics.axis) - thumbLength).coerceAtLeast(0)
    val thumbStart = trackBounds.mainAxisStart(metrics.axis) + if (metrics.maxValue == 0) {
        0
    } else {
        thumbTravel * metrics.state.value / metrics.maxValue
    }

    return when (metrics.axis) {
        StackAxis.VERTICAL -> Rect(
            x = trackBounds.x,
            y = thumbStart,
            width = trackBounds.width,
            height = thumbLength
        )
        StackAxis.HORIZONTAL -> Rect(
            x = thumbStart,
            y = trackBounds.y,
            width = thumbLength,
            height = trackBounds.height
        )
    }
}

private fun resolveScrollTrackBounds(
    scrollArea: Rect,
    viewportBounds: Rect,
    gutterSize: Int,
    axis: StackAxis
): Rect? {
    if (gutterSize <= 0 || scrollArea.height <= 0 || scrollArea.width <= 0) {
        return null
    }

    return when (axis) {
        StackAxis.VERTICAL -> Rect(
            x = viewportBounds.x + viewportBounds.width + ScrollbarTrackInset,
            y = scrollArea.y,
            width = ScrollbarTrackWidth.coerceAtMost(gutterSize),
            height = scrollArea.height
        )
        StackAxis.HORIZONTAL -> Rect(
            x = scrollArea.x,
            y = viewportBounds.y + viewportBounds.height + ScrollbarTrackInset,
            width = scrollArea.width,
            height = ScrollbarTrackWidth.coerceAtMost(gutterSize)
        )
    }
}

private fun Rect.mainAxisStart(axis: StackAxis): Int {
    return when (axis) {
        StackAxis.VERTICAL -> y
        StackAxis.HORIZONTAL -> x
    }
}

