package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The shape of a [Heightfield]'s floors around a block of [columns] × [rows] cells from ([column0],
 * [row0]), as one [GpuSceneMesh.shading] per cell corner: the normal of the ground there, smoothed
 * over the cells around it so steps of a block read as a slope, and how much of the sky it sees,
 * found by looking for the highest horizon a few cells away in eight directions. Both are averaged
 * from the four cells meeting at a corner, so neighbouring tops shade into each other.
 */
internal class ColumnRelief(
    private val field: Heightfield,
    private val column0: Int,
    private val row0: Int,
    private val columns: Int,
    private val rows: Int,
) {
    /** Cells around the block, one more each side, so corners at its edge have all four. */
    private val wide = columns + 2
    private val leanX = FloatArray(wide * (rows + 2))
    private val leanZ = FloatArray(leanX.size)
    private val sky = FloatArray(leanX.size)
    private val known = BooleanArray(leanX.size)

    init {
        for (row in -1..rows) for (column in -1..columns) {
            val c = column0 + column
            val r = row0 + row
            if (!field.present(c, r)) continue
            val at = (row + 1) * wide + column + 1
            known[at] = true
            val here = field.floor(c, r)
            fun h(dc: Int, dr: Int) =
                if (field.present(c + dc, r + dr)) field.floor(c + dc, r + dr) else here
            // Sobel: the slope over the eight cells around, the nearer ones counting double.
            val gx =
                (h(1, -1) + 2 * h(1, 0) + h(1, 1) - h(-1, -1) - 2 * h(-1, 0) - h(-1, 1)) /
                    (SOBEL_WEIGHT * field.step)
            val gz =
                (h(-1, 1) + 2 * h(0, 1) + h(1, 1) - h(-1, -1) - 2 * h(0, -1) - h(1, -1)) /
                    (SOBEL_WEIGHT * field.step)
            val length = sqrt(gx * gx + gz * gz + 1)
            leanX[at] = -gx / length
            leanZ[at] = -gz / length
            sky[at] = skyAt(c, r, here)
        }
    }

    /** The share of the sky a floor at [here] sees, after [OCCLUSION_STRENGTH]. */
    private fun skyAt(column: Int, row: Int, here: Int): Float {
        var seen = 0f
        for ((dc, dr) in DIRECTIONS) {
            val across = sqrt((dc * dc + dr * dr).toFloat()) * field.step
            var slope = 0f
            for (distance in DISTANCES) {
                val c = column + dc * distance
                val r = row + dr * distance
                if (field.present(c, r))
                    slope = max(slope, (field.floor(c, r) - here) / (distance * across))
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
        /** Cells past a block that its relief looks at: the horizon's reach, and one more. */
        val REACH = DISTANCES_MAX + 1

        private const val SOBEL_WEIGHT = 8f
        private const val OCCLUSION_STRENGTH = 0.7f
        private const val DISTANCES_MAX = 8
        private val DISTANCES = intArrayOf(1, 2, 4, DISTANCES_MAX)
        private val DIRECTIONS =
            listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
    }
}
