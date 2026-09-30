package io.github.fopwoc.mods.framework.render

/** Immutable camera matrices from a rendered world frame, in OpenGL column-major order. */
class WorldProjection(
    val x: Double,
    val y: Double,
    val z: Double,
    view: FloatArray,
    projection: FloatArray,
) {
    private val view = view.copyOf().also { require(it.size == 16) }
    private val projection = projection.copyOf().also { require(it.size == 16) }

    /** Camera-space position and homogeneous clip coordinates, before viewport conversion. */
    fun project(x: Double, y: Double, z: Double): Point {
        // Subtract in double precision before applying Minecraft's float matrices.
        val dx = x - this.x
        val dy = y - this.y
        val dz = z - this.z
        val vx = view.component(0, dx, dy, dz, 1.0)
        val vy = view.component(1, dx, dy, dz, 1.0)
        val vz = view.component(2, dx, dy, dz, 1.0)
        val vw = view.component(3, dx, dy, dz, 1.0)
        return Point(
            projection.component(0, vx, vy, vz, vw),
            projection.component(1, vx, vy, vz, vw),
            projection.component(3, vx, vy, vz, vw),
            view.component(0, dx, dy, dz, 0.0),
            -view.component(2, dx, dy, dz, 0.0),
        )
    }

    data class Point(
        val clipX: Double,
        val clipY: Double,
        val clipW: Double,
        val right: Double,
        val forward: Double,
    )

    private fun FloatArray.component(row: Int, x: Double, y: Double, z: Double, w: Double) =
        this[row] * x + this[4 + row] * y + this[8 + row] * z + this[12 + row] * w
}
