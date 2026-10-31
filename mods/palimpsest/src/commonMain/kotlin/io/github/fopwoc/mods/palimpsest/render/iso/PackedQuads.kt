package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh

/**
 * Collects quads for one [GpuSceneMesh]: corners in steps across x and z and sixteenths of a block
 * up y, four corners in winding order, each with a colour and a shading — one for the whole quad
 * through [quad], or corner by corner through [corner], four in a row making a quad.
 */
internal class PackedQuads {
    private var vertices = IntArray(INITIAL_VERTICES)
    private var colors = IntArray(INITIAL_VERTICES)
    private var shadings = IntArray(INITIAL_VERTICES)
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
        shading: Int = GpuSceneMesh.OPEN,
    ) {
        corner(color, shading, ax, ay, az)
        corner(color, shading, bx, by, bz)
        corner(color, shading, cx, cy, cz)
        corner(color, shading, dx, dy, dz)
    }

    fun corner(color: Int, shading: Int, x: Int, y: Int, z: Int) {
        if (count == colors.size) {
            vertices = vertices.copyOf(vertices.size * 2)
            colors = colors.copyOf(colors.size * 2)
            shadings = shadings.copyOf(shadings.size * 2)
        }
        vertices[count] = GpuSceneMesh.vertex(x, z, y)
        colors[count] = color
        shadings[count] = shading
        count++
    }

    fun build(originX: Double, originZ: Double, scale: Int, translucent: Boolean = false) =
        GpuSceneMesh(
            originX,
            0.0,
            originZ,
            scale,
            vertices.copyOf(count),
            colors.copyOf(count),
            translucent,
            shadings.copyOf(count),
        )

    private companion object {
        const val INITIAL_VERTICES = 1 shl 12
    }
}
