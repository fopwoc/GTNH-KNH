package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

/** Z-order of chunk coordinates, so consecutive chunks in a sorted list are spatial neighbours. */
fun morton(x: Int, z: Int): Long {
    var result = 0L
    for (bit in 0 until 32) {
        result =
            result or
                ((x.toLong() ushr bit and 1) shl (2 * bit)) or
                ((z.toLong() ushr bit and 1) shl (2 * bit + 1))
    }
    return result
}
