package io.github.fopwoc.mods.framework.ui.compose.canvas

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Flat-coloured quads in world space around ([originX], [originY], [originZ]), packed small: four
 * vertices make a quad, each one [Int] in [vertices] and one colour in [colors]. A vertex holds x
 * and z as unsigned bytes in steps of [scale] blocks, and y as a signed short in sixteenths of a
 * block; build them with [vertex]. A colour is unlit RGB, its top byte the face's direction, shine
 * and how see-through it is instead of alpha; build them with [color]. Each vertex also has one
 * [shading] [Int]: how an up face leans, so terrain lights as smooth slopes rather than flat steps,
 * and how much of the sky it sees; build them with [shading]. The scene's light does the rest.
 * Twelve bytes a vertex keep thousands of terrain chunks affordable.
 *
 * A [translucent] mesh, water, is drawn after every opaque one, blended over them and hiding
 * nothing behind it.
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
    val translucent: Boolean = false,
    val shading: IntArray = IntArray(vertices.size) { OPEN },
) {
    init {
        require(scale > 0)
        require(vertices.size == colors.size) { "One colour per vertex" }
        require(shading.size == colors.size) { "One shading per vertex" }
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

    /** Vertex [index]'s normal as x, y, z: its face's, leaned on an up face. */
    fun normal(index: Int): FloatArray {
        val face = face(colors[index])
        if (face != GpuSceneFace.UP) return floatArrayOf(face.x.toFloat(), 0f, face.z.toFloat())
        val x = leanX(shading[index])
        val z = leanZ(shading[index])
        return floatArrayOf(x, sqrt((1 - x * x - z * z).coerceAtLeast(0f)), z)
    }

    /** Vertex [index]'s colour lit by [light], opaque ARGB: for renderers that light on upload. */
    fun lit(index: Int, light: GpuSceneLight): Int {
        val color = colors[index]
        val (x, y, z) = normal(index)
        val brightness = light.brightness(x, y, z, occlusion(shading[index]))
        val r = ((color shr RED_SHIFT and BYTE) * brightness).toInt()
        val g = ((color shr GREEN_SHIFT and BYTE) * brightness).toInt()
        val b = ((color and BYTE) * brightness).toInt()
        val alpha = (opacity(color) * BYTE).toInt()
        return (alpha shl LOOK_SHIFT) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
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
        private const val CLEAR_SHIFT = 4
        private const val CLEAR_STEPS = 16
        private const val CLEAR_MASK = 0xF
        private val FACES = GpuSceneFace.entries.toTypedArray()
        private const val LEAN_STEPS = 127f
        private const val MAX_LEAN = 0.95f
        private const val OCCLUSION_SHIFT = 16

        /** The [shading] of a face that does not lean and sees the whole sky. */
        const val OPEN = BYTE shl OCCLUSION_SHIFT

        /**
         * How an up face's normal leans towards +x by [leanX] and +z by [leanZ], the normal's own x
         * and z, and what share of the sky it sees, [occlusion] 1 for all of it.
         */
        fun shading(leanX: Float, leanZ: Float, occlusion: Float = 1f): Int {
            val length = sqrt(leanX * leanX + leanZ * leanZ)
            val cut = if (length > MAX_LEAN) MAX_LEAN / length else 1f
            val x = (leanX * cut * LEAN_STEPS).roundToInt()
            val z = (leanZ * cut * LEAN_STEPS).roundToInt()
            val sky = (occlusion.coerceIn(0f, 1f) * BYTE).roundToInt()
            return (x and BYTE) or ((z and BYTE) shl Z_SHIFT) or (sky shl OCCLUSION_SHIFT)
        }

        /** The normal's x of an up face with [shading]. */
        fun leanX(shading: Int): Float = shading.toByte() / LEAN_STEPS

        /** The normal's z of an up face with [shading]. */
        fun leanZ(shading: Int): Float = (shading shr Z_SHIFT).toByte() / LEAN_STEPS

        /** The share of the sky a face with [shading] sees, 0 to 1. */
        fun occlusion(shading: Int): Float =
            (shading ushr OCCLUSION_SHIFT and BYTE) / BYTE.toFloat()

        /**
         * An unlit [rgb] on a face pointing [face], [shiny] or not, and [opacity] from 1 down to
         * 1/16: anything less than opaque belongs in a translucent mesh.
         */
        fun color(rgb: Int, face: GpuSceneFace, shiny: Boolean = false, opacity: Float = 1f): Int {
            val clear = ((1 - opacity) * CLEAR_STEPS).roundToInt().coerceIn(0, CLEAR_MASK)
            val look = face.ordinal or (if (shiny) SHINY else 0) or (clear shl CLEAR_SHIFT)
            return (rgb and RGB) or (look shl LOOK_SHIFT)
        }

        /** How opaque a [color] is, 1/16 to 1. */
        fun opacity(color: Int): Float =
            1 - (color ushr (LOOK_SHIFT + CLEAR_SHIFT) and CLEAR_MASK) / CLEAR_STEPS.toFloat()

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
