package io.github.fopwoc.mods.framework.ui.compose.canvas

/**
 * Flat-coloured triangles in the canvas's local GUI coordinates, drawn in order with no depth test:
 * whoever builds the mesh sorts it back to front. Three vertices make a triangle; [positions] holds
 * x and y per vertex, [colors] one ARGB per vertex, blended across the triangle.
 *
 * The mesh owns both arrays: it is built once per change and drawn as is, so the caller must not
 * write to them afterwards.
 */
class GpuMesh(val positions: FloatArray, val colors: IntArray) {
    init {
        require(positions.size == colors.size * 2) { "Two coordinates per colour" }
        require(colors.size % 3 == 0) { "Three vertices per triangle" }
    }

    val vertexCount: Int
        get() = colors.size

    companion object {
        val EMPTY = GpuMesh(FloatArray(0), IntArray(0))
    }
}
