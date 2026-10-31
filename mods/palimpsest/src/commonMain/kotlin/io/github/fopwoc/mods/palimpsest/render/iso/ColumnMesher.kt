package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import io.github.fopwoc.mods.palimpsest.render.TerrainShader

/**
 * Turns part of a [Heightfield] into world-space quads, the same for every camera: the GPU sorts
 * them by depth. Each column gets its top, hillshaded from the north-west, and every side that
 * rises above the neighbour there. Sides at the edge of what was seen drop to [floor], so the
 * explored world stands like a model on a table.
 */
object ColumnMesher {
    /** Where the edge of the explored world drops to: [SKIRT_BLOCKS] below its lowest column. */
    fun floor(field: Heightfield): Int {
        var lowest = Int.MAX_VALUE
        for (at in field.tops.indices) if (field.colors[at] != 0)
            lowest = minOf(lowest, field.tops[at])
        return (if (lowest == Int.MAX_VALUE) 0 else lowest) - SKIRT_BLOCKS
    }

    /** The columns from ([column0], [row0]), [columns] × [rows] of them, clipped to the field. */
    @Suppress("LongParameterList")
    fun mesh(
        field: Heightfield,
        floor: Int,
        column0: Int,
        row0: Int,
        columns: Int,
        rows: Int,
    ): GpuSceneMesh {
        val step = field.step
        val originX = field.originX + column0 * step
        val originZ = field.originZ + row0 * step
        val out = QuadBuilder()
        for (row in maxOf(row0, 0) until minOf(row0 + rows, field.rows)) {
            for (column in maxOf(column0, 0) until minOf(column0 + columns, field.columns)) {
                if (!field.present(column, row)) continue
                val top = field.top(column, row)
                val color = field.colors[row * field.columns + column]
                val x0 = ((column - column0) * step).toFloat()
                val z0 = ((row - row0) * step).toFloat()
                val x1 = x0 + step
                val z1 = z0 + step
                val y = top.toFloat()
                // Checkered by world cell, so the pattern stays put when the field moves.
                val parity =
                    (Math.floorDiv(field.originX, step) +
                        column +
                        Math.floorDiv(field.originZ, step) +
                        row) and 1
                val lit =
                    TerrainShader.shade(
                        color,
                        TerrainShader.hillshade(
                            rise(field, top, column - 1, row),
                            rise(field, top, column, row - 1),
                            parity,
                        ),
                    )
                out.quad(lit, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1)
                for ((dx, dz) in SIDES) {
                    val low = lowerEdge(field, floor, column + dx, row + dz)
                    if (low >= y) continue
                    val shade = TerrainShader.shade(color, SIDE_SHADES.getValue(dx to dz))
                    when {
                        dx > 0 -> out.quad(shade, x1, y, z0, x1, y, z1, x1, low, z1, x1, low, z0)
                        dx < 0 -> out.quad(shade, x0, y, z1, x0, y, z0, x0, low, z0, x0, low, z1)
                        dz > 0 -> out.quad(shade, x1, y, z1, x0, y, z1, x0, low, z1, x1, low, z1)
                        else -> out.quad(shade, x0, y, z0, x1, y, z0, x1, low, z0, x0, low, z0)
                    }
                }
            }
        }
        return out.build(originX.toDouble(), originZ.toDouble())
    }

    private fun rise(field: Heightfield, top: Int, column: Int, row: Int): Int =
        if (field.present(column, row)) (top - field.top(column, row)) / field.step else 0

    private fun lowerEdge(field: Heightfield, floor: Int, column: Int, row: Int): Float =
        (if (field.present(column, row)) field.top(column, row) else floor).toFloat()

    /**
     * Sides are darker than tops, the ones turned to the north-west light less so, so the same
     * cliff reads lit or shaded whichever way the map is turned.
     */
    private fun sideShade(normalX: Int, normalZ: Int): Int {
        val towardsLight = -(normalX + normalZ) * LIGHT_AXIS
        return (255 * (SIDE_BASE + SIDE_LIGHT * towardsLight)).toInt()
    }

    private class QuadBuilder {
        private var positions = FloatArray(INITIAL_VERTICES * 3)
        private var colors = IntArray(INITIAL_VERTICES)
        private var count = 0

        @Suppress("LongParameterList")
        fun quad(
            color: Int,
            ax: Float,
            ay: Float,
            az: Float,
            bx: Float,
            by: Float,
            bz: Float,
            cx: Float,
            cy: Float,
            cz: Float,
            dx: Float,
            dy: Float,
            dz: Float,
        ) {
            if (count + 4 > colors.size) {
                positions = positions.copyOf(positions.size * 2)
                colors = colors.copyOf(colors.size * 2)
            }
            vertex(color, ax, ay, az)
            vertex(color, bx, by, bz)
            vertex(color, cx, cy, cz)
            vertex(color, dx, dy, dz)
        }

        private fun vertex(color: Int, x: Float, y: Float, z: Float) {
            positions[count * 3] = x
            positions[count * 3 + 1] = y
            positions[count * 3 + 2] = z
            colors[count] = color
            count++
        }

        fun build(originX: Double, originZ: Double): GpuSceneMesh =
            GpuSceneMesh(originX, 0.0, originZ, positions.copyOf(count * 3), colors.copyOf(count))
    }

    private val SIDES = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val SIDE_SHADES = SIDES.associateWith { (dx, dz) -> sideShade(dx, dz) }

    private const val SKIRT_BLOCKS = 6
    private const val SIDE_BASE = 0.62
    private const val SIDE_LIGHT = 0.18
    /** Each axis' share of the north-west light, 1 / √2. */
    private const val LIGHT_AXIS = 0.7071
    private const val INITIAL_VERTICES = 1 shl 12
}
