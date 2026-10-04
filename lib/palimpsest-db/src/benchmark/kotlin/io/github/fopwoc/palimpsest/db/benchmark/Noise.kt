package io.github.fopwoc.palimpsest.db.benchmark

/** Deterministic value noise in 0..1 and per-point hashes, all derived from one seed. */
class Noise(private val seed: Long) {
    fun hash(x: Int, y: Int, z: Int): Double {
        var h =
            seed xor
                (x * -0x61c8864680b583ebL) xor
                (y * 0x2545F4914F6CDD1DL) xor
                (z * -0x40a7b892e31b1a47L)
        h = (h xor (h ushr 31)) * -0x40a7b892e31b1a47L
        h = (h xor (h ushr 29)) * 0x2545F4914F6CDD1DL
        return (h ushr 11) * UNIT
    }

    fun at(x: Double, z: Double): Double {
        val x0 = kotlin.math.floor(x).toInt()
        val z0 = kotlin.math.floor(z).toInt()
        val fx = smooth(x - x0)
        val fz = smooth(z - z0)
        val top = lerp(hash(x0, 0, z0), hash(x0 + 1, 0, z0), fx)
        val bottom = lerp(hash(x0, 0, z0 + 1), hash(x0 + 1, 0, z0 + 1), fx)
        return lerp(top, bottom, fz)
    }

    fun at(x: Double, y: Double, z: Double): Double {
        val x0 = kotlin.math.floor(x).toInt()
        val y0 = kotlin.math.floor(y).toInt()
        val z0 = kotlin.math.floor(z).toInt()
        val fx = smooth(x - x0)
        val fy = smooth(y - y0)
        val fz = smooth(z - z0)
        fun plane(y: Int): Double {
            val top = lerp(hash(x0, y, z0), hash(x0 + 1, y, z0), fx)
            val bottom = lerp(hash(x0, y, z0 + 1), hash(x0 + 1, y, z0 + 1), fx)
            return lerp(top, bottom, fz)
        }
        return lerp(plane(y0), plane(y0 + 1), fy)
    }

    private fun smooth(t: Double) = t * t * (3 - 2 * t)

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    private companion object {
        const val UNIT = 1.0 / (1L shl 53)
    }
}
