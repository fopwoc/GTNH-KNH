package io.github.fopwoc.palimpsest.db.index

import io.github.fopwoc.palimpsest.db.ChunkPos

/** A 32×32-chunk region, the unit the index is split and loaded by. */
internal data class RegionKey(val x: Int, val z: Int) {
    val fileName: String
        get() = "r.$x.$z.idx"

    companion object {
        const val SIDE = 32
        const val CHUNKS = SIDE * SIDE

        fun of(pos: ChunkPos) = RegionKey(pos.x shr 5, pos.z shr 5)

        /** The chunk's index inside its region. */
        fun local(pos: ChunkPos): Int = ((pos.z and 31) shl 5) or (pos.x and 31)
    }
}
