package io.github.fopwoc.mods.framework.ui.compose.canvas

/**
 * Flat-coloured quads in world space around ([originX], [originY], [originZ]): four vertices make a
 * quad, [positions] holds x, y and z per vertex relative to the origin, [colors] one ARGB per
 * vertex. Positions stay small next to the origin, so floats keep their precision far from zero.
 *
 * The mesh owns both arrays and is uploaded once: a renderer keeps its GPU copy for as long as the
 * same mesh object keeps being drawn, so a caller reuses meshes that did not change.
 */
class GpuSceneMesh(
    val originX: Double,
    val originY: Double,
    val originZ: Double,
    val positions: FloatArray,
    val colors: IntArray,
) {
    init {
        require(positions.size == colors.size * 3) { "Three coordinates per colour" }
        require(colors.size % 4 == 0) { "Four vertices per quad" }
    }

    val vertexCount: Int
        get() = colors.size

    val quadCount: Int
        get() = colors.size / 4
}
