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
            onPress = { pressX, pressY, button ->
                if (button != 0) {
                    InputPressResult.Ignored
                } else {
                    val session = ScrollDragSession(
                        state = metrics.state,
                        trackStart = trackBounds.mainAxisStart(metrics.axis),
                        trackLength = trackBounds.mainAxisSize(metrics.axis),
                        thumbLength = thumbBounds.mainAxisSize(metrics.axis),
                        maxValue = metrics.maxValue,
                        grabOffset = when (metrics.axis) {
                            StackAxis.VERTICAL -> pressY - thumbBounds.y
                            StackAxis.HORIZONTAL -> pressX - thumbBounds.x
                        }
                    )
                    InputPressResult.captured(
                        ActivePointerSession(
                            button = button,
                            onDragHandler = { dragX, dragY ->
                                session.dragTo(
                                    when (metrics.axis) {
                                        StackAxis.VERTICAL -> dragY
                                        StackAxis.HORIZONTAL -> dragX
                                    }
                                )
                            }
                        )
                    )
                }
            }
        )
    )
}

private fun Rect.mainAxisStart(axis: StackAxis): Int {
    return when (axis) {
        StackAxis.VERTICAL -> y
        StackAxis.HORIZONTAL -> x
    }
}

