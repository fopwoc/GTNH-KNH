package io.github.fopwoc.mods.framework.ui.compose.layout.scroll

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.StackAxis
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import kotlin.math.roundToInt

internal const val ScrollbarTrackWidth: Int = 4
internal const val ScrollbarTrackInset: Int = 2
internal const val ScrollbarGutterWidth: Int = ScrollbarTrackWidth + (ScrollbarTrackInset * 2)

internal data class ScrollMetrics(
    val axis: StackAxis,
    val scrollArea: Rect,
    val viewportBounds: Rect,
    val trackBounds: Rect?,
    val contentMainAxisSize: Int,
    val state: ScrollState
) {
    val maxValue: Int
        get() = (contentMainAxisSize - viewportBounds.mainAxisSize(axis)).coerceAtLeast(0)
}

internal data class ScrollDragSession(
    val state: ScrollState,
    val trackStart: Int,
    val trackLength: Int,
    val thumbLength: Int,
    val maxValue: Int,
    val grabOffset: Int
) {
    fun dragTo(pointerMainAxis: Int): Boolean {
        if (maxValue <= 0) {
            return false
        }

        val availableTravel = (trackLength - thumbLength).coerceAtLeast(0)
        if (availableTravel == 0) {
            return state.scrollTo(0)
        }

        val thumbStart = (pointerMainAxis - grabOffset).coerceIn(trackStart, trackStart + availableTravel)
        val progress = (thumbStart - trackStart).toDouble() / availableTravel.toDouble()
        return state.scrollTo((progress * maxValue).roundToInt())
    }
}

internal fun Rect.mainAxisSize(axis: StackAxis): Int {
    return when (axis) {
        StackAxis.HORIZONTAL -> width
        StackAxis.VERTICAL -> height
    }
}

internal fun Rect.crossAxisSize(axis: StackAxis): Int {
    return when (axis) {
        StackAxis.HORIZONTAL -> height
        StackAxis.VERTICAL -> width
    }
}


