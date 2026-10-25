package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuMesh
import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import kotlin.math.abs

/**
 * Turns a [Heightfield] into triangles for one camera, back to front so no depth test is needed.
 *
 * Rows run from the far side towards the viewer and, within a row, columns do the same. That order
 * is enough for a grid of columns at any yaw: two cells always differ along x or z, and along that
 * axis only the nearer one can hide the other. Each column gets its top, lit like the flat map, and
 * at most the two sides facing the viewer, only where they rise above the neighbour in front. Sides
 * at the edge of what was seen drop to a common floor, so the explored world stands like a model on
 * a table.
 */
object ColumnMesher {
    fun mesh(field: Heightfield, camera: IsoCamera): GpuMesh {
        val out = MeshBuilder()
        val step = field.step
        val floor =
            (field.tops.filterIndexed { at, _ -> field.colors[at] != 0 }.minOrNull() ?: 0) -
                SKIRT_BLOCKS
        val nearX = if (camera.towardX >= 0) 1 else -1
        val nearZ = if (camera.towardZ >= 0) 1 else -1
        val showX = camera.pitch < IsoCamera.MAX_PITCH && abs(camera.towardX) > EDGE_ON
        val showZ = camera.pitch < IsoCamera.MAX_PITCH && abs(camera.towardZ) > EDGE_ON
        val sideX = sideShade(nearX, 0)
        val sideZ = sideShade(0, nearZ)
        val margin = step * camera.pixelsPerBlock * 2
        for (r in 0 until field.rows) {
            val row = if (nearZ > 0) r else field.rows - 1 - r
            for (c in 0 until field.columns) {
                val column = if (nearX > 0) c else field.columns - 1 - c
                if (!field.present(column, row)) continue
                val top = field.top(column, row)
                val color = field.colors[row * field.columns + column]
                val x0 = (field.originX + column * step).toDouble()
                val z0 = (field.originZ + row * step).toDouble()
                val x1 = x0 + step
                val z1 = z0 + step
                val midX = camera.screenX(x0 + step / 2.0, z0 + step / 2.0)
                if (midX < -margin || midX > camera.width + margin) continue
                val topY = camera.screenY(x0 + step / 2.0, top.toDouble(), z0 + step / 2.0)
                val floorY = camera.screenY(x0 + step / 2.0, floor.toDouble(), z0 + step / 2.0)
                if (topY > camera.height + margin || floorY < -margin) continue

                val westRise = rise(field, top, column - 1, row)
                val northRise = rise(field, top, column, row - 1)
                val lit =
                    TerrainShader.shade(
                        color,
                        TerrainShader.hillshade(westRise, northRise, (column + row) and 1),
                    )
                val y = top.toDouble()
                out.quad(camera, lit, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1)

                if (showX) {
                    val low = lowerEdge(field, floor, column + nearX, row)
                    if (low < top) {
                        val x = if (nearX > 0) x1 else x0
                        val shade = TerrainShader.shade(color, sideX)
                        out.quad(camera, shade, x, y, z0, x, y, z1, x, low, z1, x, low, z0)
                    }
                }
                if (showZ) {
                    val low = lowerEdge(field, floor, column, row + nearZ)
                    if (low < top) {
                        val z = if (nearZ > 0) z1 else z0
                        val shade = TerrainShader.shade(color, sideZ)
                        out.quad(camera, shade, x0, y, z, x1, y, z, x1, low, z, x0, low, z)
                    }
                }
            }
        }
        return out.build()
    }

    private fun rise(field: Heightfield, top: Int, column: Int, row: Int): Int =
        if (field.present(column, row)) (top - field.top(column, row)) / field.step else 0

    private fun lowerEdge(field: Heightfield, floor: Int, column: Int, row: Int): Double =
        (if (field.present(column, row)) field.top(column, row) else floor).toDouble()

    /**
     * Sides are darker than tops, the ones turned to the north-west light less so, so the same
     * cliff reads lit or shaded whichever way the map is turned.
     */
    private fun sideShade(normalX: Int, normalZ: Int): Int {
        val towardsLight = -(normalX + normalZ) * LIGHT_AXIS
        return (255 * (SIDE_BASE + SIDE_LIGHT * towardsLight)).toInt()
    }

    private class MeshBuilder {
        private var positions = FloatArray(INITIAL_VERTICES * 2)
        private var colors = IntArray(INITIAL_VERTICES)
        private var count = 0

        @Suppress("LongParameterList")
        fun quad(
            camera: IsoCamera,
            color: Int,
            ax: Double,
            ay: Double,
            az: Double,
            bx: Double,
            by: Double,
            bz: Double,
            cx: Double,
            cy: Double,
            cz: Double,
            dx: Double,
            dy: Double,
            dz: Double,
        ) {
            if (count + 6 > colors.size) {
                positions = positions.copyOf(positions.size * 2)
                colors = colors.copyOf(colors.size * 2)
            }
            vertex(camera, color, ax, ay, az)
            vertex(camera, color, bx, by, bz)
            vertex(camera, color, cx, cy, cz)
            vertex(camera, color, ax, ay, az)
            vertex(camera, color, cx, cy, cz)
            vertex(camera, color, dx, dy, dz)
        }

        private fun vertex(camera: IsoCamera, color: Int, x: Double, y: Double, z: Double) {
            positions[count * 2] = camera.screenX(x, z).toFloat()
            positions[count * 2 + 1] = camera.screenY(x, y, z).toFloat()
            colors[count] = color
            count++
        }

        fun build(): GpuMesh =
            if (count == 0) GpuMesh.EMPTY
            else GpuMesh(positions.copyOf(count * 2), colors.copyOf(count))
    }

    /** Blocks the edge of the explored world drops below its lowest column. */
    private const val SKIRT_BLOCKS = 6
    private const val SIDE_BASE = 0.62
    private const val SIDE_LIGHT = 0.18
    /** Each axis' share of the north-west light, 1 / √2. */
    private const val LIGHT_AXIS = 0.7071
    /** Sides turned further from the viewer than this are edge-on and drawn as nothing. */
    private const val EDGE_ON = 1e-6
    private const val INITIAL_VERTICES = 1 shl 16
}
