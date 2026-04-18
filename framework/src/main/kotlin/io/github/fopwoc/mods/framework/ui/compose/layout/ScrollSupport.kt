package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import kotlin.math.roundToInt

internal const val ScrollbarTrackWidth: Int = 4
internal const val ScrollbarTrackInset: Int = 2
internal const val ScrollbarGutterWidth: Int = ScrollbarTrackWidth + (ScrollbarTrackInset * 2)

internal data class ScrollMetrics(
    val scrollArea: Rect,
    val viewportBounds: Rect,
    val trackBounds: Rect?,
    val contentHeight: Int,
    val state: ScrollState
) {
    val maxValue: Int
        get() = (contentHeight - viewportBounds.height).coerceAtLeast(0)
}

internal data class ScrollDragSession(
    val state: ScrollState,
    val trackTop: Int,
    val trackHeight: Int,
    val thumbHeight: Int,
    val maxValue: Int,
    val grabOffsetY: Int
) {
    fun dragTo(mouseY: Int): Boolean {
        if (maxValue <= 0) {
            return false
        }

        val availableTravel = (trackHeight - thumbHeight).coerceAtLeast(0)
        if (availableTravel == 0) {
            return state.scrollTo(0)
        }

        val thumbTop = (mouseY - grabOffsetY).coerceIn(trackTop, trackTop + availableTravel)
        val progress = (thumbTop - trackTop).toDouble() / availableTravel.toDouble()
        return state.scrollTo((progress * maxValue).roundToInt())
    }
}


