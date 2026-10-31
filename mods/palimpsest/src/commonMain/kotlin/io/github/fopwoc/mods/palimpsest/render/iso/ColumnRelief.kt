package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The large shape of a [Heightfield]'s floors around a block of [columns] × [rows] cells from
 * ([column0], [row0]), as one [GpuSceneMesh.shading] per cell corner: the normal of the ground
 * there, smoothed over the cells around it so steps of a block read as a slope, and how much of the
 * sky it sees, found by looking for the highest horizon a few cells away in eight directions. Both
 * are averaged from the four cells meeting at a corner, so neighbouring tops shade into each other.
 *
 * Trees are not ground: a forest is a field of narrow spikes, and read as is every canopy would
 * turn into a dome and every gap into a pit. So the heights are opened and closed first — bumps and
 * gaps narrower than a few blocks are shaved off and filled in — and only hills, valleys and cliffs
 * remain. The small relief of single trees is the hillshade baked into their colour.
 */
internal class ColumnRelief(
    field: Heightfield,
    private val column0: Int,
    private val row0: Int,
    private val columns: Int,
    rows: Int,
) {
    private val step = field.step
    /** The filtered heights, [REACH] cells past the block on every side; [ABSENT] where unseen. */
    private val across = columns + 2 * REACH
    private val heights = IntArray(across * (rows + 2 * REACH))
    /** Per cell, one more each side of the block, so corners at its edge have all four. */
    private val wide = columns + 2
    private val leanX = FloatArray(wide * (rows + 2))
    private val leanZ = FloatArray(leanX.size)
    private val sky = FloatArray(leanX.size)
    private val known = BooleanArray(leanX.size)

    init {
        val tall = rows + 2 * REACH
        for (row in 0 until tall) for (column in 0 until across) {
            val c = column0 - REACH + column
            val r = row0 - REACH + row
            heights[row * across + column] = if (field.present(c, r)) field.floor(c, r) else ABSENT
        }
        val radius = if (step == 1) FINE_RADIUS else 1
        val lowest = { a: Int, b: Int -> min(a, b) }
        val highest = { a: Int, b: Int -> max(a, b) }
        // Opened, shaving off bumps, then closed, filling in gaps.
        filter(tall, radius, lowest)
        filter(tall, radius, highest)
        filter(tall, radius, highest)
        filter(tall, radius, lowest)
        for (row in -1..rows) for (column in -1..columns) {
            val here = height(column, row)
            if (here == ABSENT) continue
            val at = (row + 1) * wide + column + 1
            known[at] = true
            fun h(dc: Int, dr: Int) =
                height(column + dc, row + dr).let { if (it == ABSENT) here else it }
            // Sobel: the slope over the eight cells around, the nearer ones counting double.
            val gx =
                (h(1, -1) + 2 * h(1, 0) + h(1, 1) - h(-1, -1) - 2 * h(-1, 0) - h(-1, 1)) /
                    (SOBEL_WEIGHT * step)
            val gz =
                (h(-1, 1) + 2 * h(0, 1) + h(1, 1) - h(-1, -1) - 2 * h(0, -1) - h(1, -1)) /
                    (SOBEL_WEIGHT * step)
            val length = sqrt(gx * gx + gz * gz + 1)
            leanX[at] = -gx / length
            leanZ[at] = -gz / length
            sky[at] = skyAt(column, row, here)
        }
    }

    /** The filtered height of the cell [column] and [row] cells from the block's first. */
    private fun height(column: Int, row: Int): Int =
        heights[(row + REACH) * across + column + REACH]

    /**
     * Replaces every seen height with [pick] of the seen ones within [radius] cells, first along
     * rows, then along columns.
     */
    private fun filter(tall: Int, radius: Int, pick: (Int, Int) -> Int) {
        val line = IntArray(maxOf(across, tall))
        for (row in 0 until tall) {
            for (column in 0 until across) line[column] = heights[row * across + column]
            for (column in 0 until across) {
                if (line[column] == ABSENT) continue
                var value = line[column]
                for (k in maxOf(0, column - radius)..minOf(across - 1, column + radius)) if (
                    line[k] != ABSENT
                )
                    value = pick(value, line[k])
                heights[row * across + column] = value
            }
        }
        for (column in 0 until across) {
            for (row in 0 until tall) line[row] = heights[row * across + column]
            for (row in 0 until tall) {
                if (line[row] == ABSENT) continue
                var value = line[row]
                for (k in maxOf(0, row - radius)..minOf(tall - 1, row + radius)) if (
                    line[k] != ABSENT
                )
                    value = pick(value, line[k])
                heights[row * across + column] = value
            }
        }
    }

    /** The share of the sky a floor at [here] sees, after [OCCLUSION_STRENGTH]. */
    private fun skyAt(column: Int, row: Int, here: Int): Float {
        var seen = 0f
        for ((dc, dr) in DIRECTIONS) {
            val run = sqrt((dc * dc + dr * dr).toFloat()) * step
            var slope = 0f
            for (distance in DISTANCES) {
                val there = height(column + dc * distance, row + dr * distance)
                if (there != ABSENT) slope = max(slope, (there - here) / (distance * run))
            }
            // The sky above a horizon at that slope, weighed as a cosine falls: cos² of its angle.
            seen += 1 / (1 + slope * slope)
        }
        return 1 - OCCLUSION_STRENGTH * (1 - seen / DIRECTIONS.size)
    }

    /**
     * The shading at the corner [corner] steps across and [cornerRow] steps down from the block's
     * first corner, from the cells that meet there.
     */
    fun at(corner: Int, cornerRow: Int): Int {
        var x = 0f
        var z = 0f
        var light = 0f
        var count = 0
        for (row in cornerRow..cornerRow + 1) for (column in corner..corner + 1) {
            val at = row * wide + column
            if (!known[at]) continue
            x += leanX[at]
            z += leanZ[at]
            light += sky[at]
            count++
        }
        if (count == 0) return GpuSceneMesh.OPEN
        return GpuSceneMesh.shading(x / count, z / count, light / count)
    }

    companion object {
        private const val SOBEL_WEIGHT = 8f
        private const val OCCLUSION_STRENGTH = 0.7f
        private const val DISTANCES_MAX = 8
        private val DISTANCES = intArrayOf(1, 2, 4, DISTANCES_MAX)
        private val DIRECTIONS =
            listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
        /**
         * Cells either way the filter takes its extremes from: two at full detail, so trees up to
         * five blocks across go; one when a cell is wider, where a cell is a sample anyway.
         */
        private const val FINE_RADIUS = 2
        private const val ABSENT = Int.MIN_VALUE

        /**
         * Cells past a block that its relief looks at: the horizon's reach and the corner cells
         * beyond the block, then four filter passes' worth.
         */
        const val REACH = DISTANCES_MAX + 1 + 4 * FINE_RADIUS
    }
}
