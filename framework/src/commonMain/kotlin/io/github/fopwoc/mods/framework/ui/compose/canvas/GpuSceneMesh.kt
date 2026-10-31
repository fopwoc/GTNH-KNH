package io.github.fopwoc.mods.framework.ui.compose.canvas

/**
 * Flat-coloured quads in world space around ([originX], [originY], [originZ]), packed small: four
 * vertices make a quad, each one [Int] in [vertices] and one colour in [colors]. A vertex holds x
 * and z as unsigned bytes in steps of [scale] blocks, and y as a signed short in sixteenths of a
 * block; build them with [vertex]. A colour is unlit RGB, its top byte the face's direction and
 * shine instead of alpha; build them with [color]. The scene's light does the rest. Eight bytes a
 * vertex keep thousands of terrain chunks affordable.
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

    /**
     * The box the mesh fills, in blocks from its origin: min x, y, z, then max x, y, z. Worked out
     * once, on first use.
     */
    val bounds: FloatArray by lazy {
        val box =
            floatArrayOf(
                Float.MAX_VALUE,
                Float.MAX_VALUE,
                Float.MAX_VALUE,
                -Float.MAX_VALUE,
                -Float.MAX_VALUE,
                -Float.MAX_VALUE,
            )
        for (index in 0 until vertexCount) {
            val corner = floatArrayOf(x(index), y(index), z(index))
            for (axis in 0 until AXES) {
                box[axis] = minOf(box[axis], corner[axis])
                box[axis + AXES] = maxOf(box[axis + AXES], corner[axis])
            }
        }
        box
    }

    /** Vertex [index]'s colour lit by [light], opaque ARGB: for renderers that light on upload. */
    fun lit(index: Int, light: GpuSceneLight): Int {
        val color = colors[index]
        val brightness = light.brightness(face(color))
        val r = ((color shr RED_SHIFT and BYTE) * brightness).toInt()
        val g = ((color shr GREEN_SHIFT and BYTE) * brightness).toInt()
        val b = ((color and BYTE) * brightness).toInt()
        return OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
    }

    companion object {
        /** Steps of y in a block. */
        const val SUBDIVISIONS = 16
        /** The furthest a vertex lies from the origin along x or z, in steps. */
        const val MAX_STEPS = 255
        private const val BYTE = 0xFF
        private const val Z_SHIFT = 8
        private const val Y_SHIFT = 16
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val LOOK_SHIFT = 24
        private const val SHINY = 0x8
        private const val FACE_MASK = 0x7
        private const val OPAQUE = 0xFF shl LOOK_SHIFT
        private val FACES = GpuSceneFace.entries.toTypedArray()

        /** An unlit [rgb] on a face pointing [face], [shiny] or not. */
        fun color(rgb: Int, face: GpuSceneFace, shiny: Boolean = false): Int =
            (rgb and RGB) or ((face.ordinal or if (shiny) SHINY else 0) shl LOOK_SHIFT)

        /** The way a [color]'s face points. */
        fun face(color: Int): GpuSceneFace = FACES[color ushr LOOK_SHIFT and FACE_MASK]

        /** Whether a [color]'s face is shiny. */
        fun isShiny(color: Int): Boolean = color ushr LOOK_SHIFT and SHINY != 0

        private const val RGB = 0xFFFFFF
        private const val AXES = 3

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
