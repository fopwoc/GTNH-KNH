package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component

import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.iso.ViewCube
import kotlin.math.roundToInt

/**
 * Where the view controls sit on the map canvas, shared by the drawing and the screen's pointer
 * handling: the view cube, two quarter-turn buttons under it, then the slice rail.
 */
internal object ViewControlsLayout {
    const val WIDTH = ViewCube.SIZE
    const val MARGIN = 6
    const val TURN_TOP = ViewCube.SIZE + 2
    const val TURN_HEIGHT = 16
    const val RAIL_WIDTH = 10
    const val RAIL_LEFT = (WIDTH - RAIL_WIDTH) / 2
    const val RAIL_TOP = TURN_TOP + TURN_HEIGHT + 8
    const val RAIL_HEIGHT = 128
    const val HEIGHT = RAIL_TOP + RAIL_HEIGHT + 14

    /**
     * Left edge of the controls on a canvas [screenWidth] wide with [panelWidth] taken on the
     * right.
     */
    fun left(screenWidth: Int, panelWidth: Int): Int = screenWidth - panelWidth - WIDTH - MARGIN

    const val TOP = MARGIN

    /** The Y the rail shows at [y] pixels down it; its top is "everything". */
    fun sliceAt(y: Double, range: IntRange): Int? {
        val fraction = 1 - (y / RAIL_HEIGHT).coerceIn(0.0, 1.0)
        val slice = range.first + (fraction * (range.last - range.first)).roundToInt()
        return slice.takeIf { it < range.last }
    }

    /** Pixels down the rail for [slice]; any cut sits below the top row, which is "everything". */
    fun railY(slice: Int?, range: IntRange): Int {
        val y = slice ?: return 0
        val fraction = (y - range.first).toDouble() / (range.last - range.first)
        return ((1 - fraction) * RAIL_HEIGHT).roundToInt().coerceAtLeast(1)
    }
}
