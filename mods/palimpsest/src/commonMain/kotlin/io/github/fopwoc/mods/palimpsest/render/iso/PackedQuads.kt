package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh

/**
 * Collects quads for one [GpuSceneMesh]: corners in steps across x and z and sixteenths of a block
 * up y, four corners in winding order with one colour.
 */
internal class PackedQuads {
    private var vertices = IntArray(INITIAL_VERTICES)
    private var colors = IntArray(INITIAL_VERTICES)
    private var count = 0

    val isEmpty: Boolean
        get() = count == 0

    @Suppress("LongParameterList")
    fun quad(
        color: Int,
        ax: Int,
        ay: Int,
        az: Int,
        bx: Int,
        by: Int,
        bz: Int,
        cx: Int,
        cy: Int,
        cz: Int,
        dx: Int,
        dy: Int,
        dz: Int,
    ) {
        if (count + CORNERS > colors.size) {
            vertices = vertices.copyOf(vertices.size * 2)
            colors = colors.copyOf(colors.size * 2)
        }
        vertex(color, ax, ay, az)
        vertex(color, bx, by, bz)
        vertex(color, cx, cy, cz)
        vertex(color, dx, dy, dz)
    }

    private fun vertex(color: Int, x: Int, y: Int, z: Int) {
        vertices[count] = GpuSceneMesh.vertex(x, z, y)
        colors[count] = color
        count++
    }

    fun build(originX: Double, originZ: Double, scale: Int): GpuSceneMesh =
        GpuSceneMesh(originX, 0.0, originZ, scale, vertices.copyOf(count), colors.copyOf(count))

    private companion object {
        const val CORNERS = 4
        const val INITIAL_VERTICES = 1 shl 12
    }
}
