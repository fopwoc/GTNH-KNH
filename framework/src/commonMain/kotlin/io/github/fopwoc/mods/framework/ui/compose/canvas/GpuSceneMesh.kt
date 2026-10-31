package io.github.fopwoc.mods.framework.ui.compose.canvas

/**
 * Flat-coloured quads in world space around ([originX], [originY], [originZ]), packed small: four
 * vertices make a quad, each one [Int] in [vertices] and one ARGB in [colors]. A vertex holds x and
 * z as unsigned bytes in steps of [scale] blocks, and y as a signed short in sixteenths of a block;
 * build them with [vertex]. Eight bytes a vertex keep thousands of terrain chunks affordable.
 *
 * The mesh owns both arrays and is uploaded once: a renderer keeps its GPU copy for as long as the
 * same mesh object keeps being drawn, so a caller reuses meshes that did not change.
 */
class GpuSceneMesh(
    val originX: Double,
    val originY: Double,
    val originZ: Double,
    val scale: Int,
    val vertices: IntArray,
    val colors: IntArray,
) {
    init {
        require(scale > 0)
        require(vertices.size == colors.size) { "One colour per vertex" }
        require(colors.size % 4 == 0) { "Four vertices per quad" }
    }

    val vertexCount: Int
        get() = colors.size

    val quadCount: Int
        get() = colors.size / 4

    /** Vertex [index]'s x in blocks from the origin. */
    fun x(index: Int): Float = (vertices[index] and BYTE) * scale.toFloat()

    /** Vertex [index]'s z in blocks from the origin. */
    fun z(index: Int): Float = (vertices[index] ushr Z_SHIFT and BYTE) * scale.toFloat()

    /** Vertex [index]'s y in blocks from the origin. */
    fun y(index: Int): Float = (vertices[index] shr Y_SHIFT) / SUBDIVISIONS.toFloat()

    companion object {
        /** Steps of y in a block. */
        const val SUBDIVISIONS = 16
        /** The furthest a vertex lies from the origin along x or z, in steps. */
        const val MAX_STEPS = 255
        private const val BYTE = 0xFF
        private const val Z_SHIFT = 8
        private const val Y_SHIFT = 16

        /**
         * A packed vertex [x] and [z] steps from the origin and [ySixteenths] of a block above it.
         */
        fun vertex(x: Int, z: Int, ySixteenths: Int): Int {
            require(x in 0..MAX_STEPS && z in 0..MAX_STEPS)
            require(ySixteenths in Short.MIN_VALUE..Short.MAX_VALUE)
            return x or (z shl Z_SHIFT) or (ySixteenths shl Y_SHIFT)
        }
    }
}
