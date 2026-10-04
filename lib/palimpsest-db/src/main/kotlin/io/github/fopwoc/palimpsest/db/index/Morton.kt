package io.github.fopwoc.palimpsest.db.index

/**
 * Z-order inside a region: chunk locals sorted by interleaved x and z bits. An aligned 2^k-sided
 * square of chunks is one contiguous run of this order, which is what lets the overview pick "the
 * first existing child" of any cell with a single scan.
 */
internal object Morton {
    /** [ORDER] at position m holds the region local of the m-th chunk in Z-order. */
    val ORDER =
        IntArray(RegionKey.CHUNKS).also { order ->
            for (local in 0 until RegionKey.CHUNKS) order[code(local and 31, local shr 5)] = local
        }

    fun code(x: Int, z: Int): Int {
        var code = 0
        for (bit in 0 until 16) code =
            code or (((x shr bit) and 1) shl (2 * bit)) or (((z shr bit) and 1) shl (2 * bit + 1))
        return code
    }
}
