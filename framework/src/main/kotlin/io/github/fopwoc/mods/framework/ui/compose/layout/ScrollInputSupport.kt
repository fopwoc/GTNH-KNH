package io.github.fopwoc.mods.framework.ui.compose.layout

import kotlin.math.max
import kotlin.math.min

internal fun wheelDeltaToPixels(wheelDelta: Int): Int {
    val steps = when {
        wheelDelta > 0 -> max(1, wheelDelta / 120)
        wheelDelta < 0 -> min(-1, wheelDelta / 120)
        else -> 0
    }
    return -steps * 24
}

internal fun registerScrollWheelTarget(context: RenderContext, metrics: ScrollMetrics) {
    if (metrics.maxValue <= 0) {
        return
    }

    context.registerInputTarget(
        InputTarget(
            kind = InputTargetKind.SCROLL_WHEEL,
            bounds = metrics.scrollArea,
            onWheel = { _, _, wheelDelta ->
                metrics.state.scrollBy(wheelDeltaToPixels(wheelDelta))
            }
        )
    )
}

internal fun registerScrollThumbTarget(context: RenderContext, metrics: ScrollMetrics) {
    val trackBounds = metrics.trackBounds ?: return
    val thumbBounds = resolveScrollThumbBounds(metrics) ?: return
    context.registerInputTarget(
        InputTarget(
            kind = InputTargetKind.SCROLL_THUMB,
            bounds = thumbBounds,
            onPress = { _, pressY, button ->
                if (button != 0) {
                    InputPressResult.Ignored
                } else {
                    val session = ScrollDragSession(
                        state = metrics.state,
                        trackTop = trackBounds.y,
                        trackHeight = trackBounds.height,
                        thumbHeight = thumbBounds.height,
                        maxValue = metrics.maxValue,
                        grabOffsetY = pressY - thumbBounds.y
                    )
                    InputPressResult.captured(
                        ActivePointerSession(
                            button = button,
                            onDragHandler = { _, dragY -> session.dragTo(dragY) }
                        )
                    )
                }
            }
        )
    )
}

