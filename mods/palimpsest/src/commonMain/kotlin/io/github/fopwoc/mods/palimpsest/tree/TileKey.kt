package io.github.fopwoc.mods.palimpsest.tree

/** Chunk coordinates of a tile: the world seen from above, one tile per chunk. */
data class TileKey(val x: Int, val z: Int) : Comparable<TileKey> {
    /**
     * Z-order (NW, NE, SW, SE recursively), so each square at every zoom has a stable first tile.
     */
    override fun compareTo(other: TileKey): Int {
        val x = this.x xor Int.MIN_VALUE
        val z = this.z xor Int.MIN_VALUE
        val otherX = other.x xor Int.MIN_VALUE
        val otherZ = other.z xor Int.MIN_VALUE
        val bit = Integer.highestOneBit((x xor otherX) or (z xor otherZ))
        return if ((z xor otherZ) and bit != 0) (z and bit).compareTo(otherZ and bit)
        else (x and bit).compareTo(otherX and bit)
    }
}
