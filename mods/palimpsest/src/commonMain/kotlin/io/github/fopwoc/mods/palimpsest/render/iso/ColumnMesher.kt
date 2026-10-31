package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneFace
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import io.github.fopwoc.mods.palimpsest.render.TerrainShader

/**
 * Turns part of a [Heightfield] into world-space quads, the same for every camera: the GPU sorts
 * them by depth. Each column gets its floor's top, its small relief hillshaded in from the
 * north-west and the ground's large shape from the [ColumnRelief] at its corners, and every side
 * that rises above the neighbour's floor there, all unlit: the scene's light falls on them by the
 * way they face. Water lies over its floor as a see-through surface of its own, its depth taken at
 * each corner from the columns meeting there, so it thins out towards the shore, and shines. Sides
 * at the edge of what was seen drop to [floor], so the explored world stands like a model on a
 * table.
 */
object ColumnMesher {
    /** A block of columns, and the water over them when there is any. */
    class Meshes(val solid: GpuSceneMesh, val water: GpuSceneMesh?)

    /** Where the edge of the explored world drops to: [SKIRT_BLOCKS] below its lowest floor. */
    fun floor(field: Heightfield): Int {
        var lowest = Int.MAX_VALUE
        for (at in field.tops.indices) if (field.colors[at] != 0)
            lowest = minOf(lowest, field.tops[at] - field.depths[at])
        return (if (lowest == Int.MAX_VALUE) 0 else lowest) - SKIRT_BLOCKS
    }

    /**
     * The columns from ([column0], [row0]), [columns] × [rows] of them, clipped to the field,
     * leaving out the ones [skip] names: those are drawn another way.
     */
    @Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
    fun mesh(
        field: Heightfield,
        floor: Int,
        column0: Int,
        row0: Int,
        columns: Int,
        rows: Int,
        skip: (column: Int, row: Int) -> Boolean = { _, _ -> false },
    ): Meshes {
        val step = field.step
        val originX = field.originX + column0 * step
        val originZ = field.originZ + row0 * step
        val relief = ColumnRelief(field, column0, row0, columns, rows)
        val out = PackedQuads()
        val water = PackedQuads()
        for (row in maxOf(row0, 0) until minOf(row0 + rows, field.rows)) {
            for (column in maxOf(column0, 0) until minOf(column0 + columns, field.columns)) {
                if (!field.present(column, row) || skip(column, row)) continue
                val at = row * field.columns + column
                val color = field.colors[at]
                val x0 = column - column0
                val z0 = row - row0
                val x1 = x0 + 1
                val z1 = z0 + 1
                val floorY = field.floor(column, row)
                val y = floorY * SIXTEENTHS
                // Checkered by world cell, so the pattern stays put when the field moves.
                val parity =
                    (Math.floorDiv(field.originX, step) +
                        column +
                        Math.floorDiv(field.originZ, step) +
                        row) and 1
                val hill =
                    TerrainShader.hillshade(
                        rise(field, floorY, column - 1, row),
                        rise(field, floorY, column, row - 1),
                        parity,
                    )
                val top = GpuSceneMesh.color(TerrainShader.shade(color, hill), GpuSceneFace.UP)
                out.corner(top, relief.at(x0, z0), x0, y, z0)
                out.corner(top, relief.at(x1, z0), x1, y, z0)
                out.corner(top, relief.at(x1, z1), x1, y, z1)
                out.corner(top, relief.at(x0, z1), x0, y, z1)
                for ((dx, dz) in SIDES) {
                    val low = lowerFloor(field, floor, column + dx, row + dz)
                    if (low >= y) continue
                    val shade = GpuSceneMesh.color(color, GpuSceneFace.of(dx, 0, dz))
                    out.side(shade, dx, dz, x0, z0, y, low)
                }
                val depth = field.depths[at]
                if (depth <= 0) continue
                val tinted = field.waters[at]
                val surface = field.tops[at] * SIXTEENTHS
                fun corner(cx: Int, cz: Int) {
                    val color =
                        TerrainShader.water(tinted, cornerDepth(field, column0 + cx, row0 + cz))
                    water.corner(
                        waterFace(color, GpuSceneFace.UP),
                        GpuSceneMesh.OPEN,
                        cx,
                        surface,
                        cz,
                    )
                }
                corner(x0, z0)
                corner(x1, z0)
                corner(x1, z1)
                corner(x0, z1)
                val wall = TerrainShader.water(tinted, depth.toDouble())
                for ((dx, dz) in SIDES) {
                    val low =
                        if (field.present(column + dx, row + dz))
                            maxOf(y, field.top(column + dx, row + dz) * SIXTEENTHS)
                        else y
                    if (low >= surface) continue
                    water.side(
                        waterFace(wall, GpuSceneFace.of(dx, 0, dz)),
                        dx,
                        dz,
                        x0,
                        z0,
                        surface,
                        low,
                    )
                }
            }
        }
        val x = originX.toDouble()
        val z = originZ.toDouble()
        return Meshes(
            out.build(x, z, step),
            if (water.isEmpty) null else water.build(x, z, step, translucent = true),
        )
    }

    /** A water colour with its alpha as the scene's opacity, on a face pointing [face]. */
    private fun waterFace(water: Int, face: GpuSceneFace): Int =
        GpuSceneMesh.color(
            water,
            face,
            shiny = true,
            opacity = (water ushr ALPHA_SHIFT) / OPAQUE,
        )

    /**
     * How deep the water is at the corner of cells ([column], [row]) where the four cells meeting
     * there touch: their average, dry ones counting nothing, unseen ones left out.
     */
    private fun cornerDepth(field: Heightfield, column: Int, row: Int): Double {
        var depth = 0
        var count = 0
        for (r in row - 1..row) for (c in column - 1..column) {
            if (!field.present(c, r)) continue
            depth += field.depth(c, r)
            count++
        }
        return if (count == 0) 0.0 else depth.toDouble() / count
    }

    /** A wall of a cell at ([x0], [z0]) facing ([dx], [dz]), from [high] down to [low]. */
    @Suppress("LongParameterList")
    private fun PackedQuads.side(
        color: Int,
        dx: Int,
        dz: Int,
        x0: Int,
        z0: Int,
        high: Int,
        low: Int,
    ) {
        val x1 = x0 + 1
        val z1 = z0 + 1
        when {
            dx > 0 -> quad(color, x1, high, z0, x1, high, z1, x1, low, z1, x1, low, z0)
            dx < 0 -> quad(color, x0, high, z1, x0, high, z0, x0, low, z0, x0, low, z1)
            dz > 0 -> quad(color, x1, high, z1, x0, high, z1, x0, low, z1, x1, low, z1)
            else -> quad(color, x0, high, z0, x1, high, z0, x1, low, z0, x0, low, z0)
        }
    }

    private fun rise(field: Heightfield, floor: Int, column: Int, row: Int): Int =
        if (field.present(column, row)) (floor - field.floor(column, row)) / field.step else 0

    private fun lowerFloor(field: Heightfield, floor: Int, column: Int, row: Int): Int =
        (if (field.present(column, row)) field.floor(column, row) else floor) * SIXTEENTHS

    private val SIDES = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    private const val SKIRT_BLOCKS = 6
    private const val SIXTEENTHS = GpuSceneMesh.SUBDIVISIONS
    private const val ALPHA_SHIFT = 24
    private const val OPAQUE = 255f
}
