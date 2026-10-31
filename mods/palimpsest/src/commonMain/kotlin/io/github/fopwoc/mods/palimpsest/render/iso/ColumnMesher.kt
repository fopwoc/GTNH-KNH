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

    /**
     * The columns from ([column0], [row0]), [columns] × [rows] of them, clipped to the field,
     * leaving out the ones [skip] names: those are drawn another way.
     */
    @Suppress("LongParameterList")
    fun mesh(
        field: Heightfield,
        floor: Int,
        column0: Int,
        row0: Int,
        columns: Int,
        rows: Int,
        skip: (column: Int, row: Int) -> Boolean = { _, _ -> false },
    ): GpuSceneMesh {
        val step = field.step
        val originX = field.originX + column0 * step
        val originZ = field.originZ + row0 * step
        val out = PackedQuads()
        for (row in maxOf(row0, 0) until minOf(row0 + rows, field.rows)) {
            for (column in maxOf(column0, 0) until minOf(column0 + columns, field.columns)) {
                if (!field.present(column, row) || skip(column, row)) continue
                val top = field.top(column, row)
                val color = field.colors[row * field.columns + column]
                val x0 = column - column0
                val z0 = row - row0
                val x1 = x0 + 1
                val z1 = z0 + 1
                val y = top * SIXTEENTHS
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
        return out.build(originX.toDouble(), originZ.toDouble(), step)
    }

    private fun rise(field: Heightfield, top: Int, column: Int, row: Int): Int =
        if (field.present(column, row)) (top - field.top(column, row)) / field.step else 0

    private fun lowerEdge(field: Heightfield, floor: Int, column: Int, row: Int): Int =
        (if (field.present(column, row)) field.top(column, row) else floor) * SIXTEENTHS

    private val SIDES = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val SIDE_SHADES = SIDES.associateWith { (dx, dz) -> TerrainShader.side(dx, dz) }

    private const val SKIRT_BLOCKS = 6
    private const val SIXTEENTHS = GpuSceneMesh.SUBDIVISIONS
}
