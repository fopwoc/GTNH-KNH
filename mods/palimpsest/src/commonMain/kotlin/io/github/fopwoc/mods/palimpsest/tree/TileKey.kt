package io.github.fopwoc.mods.palimpsest.tree

/** Chunk coordinates of a tile: the world seen from above, one tile per chunk. */
data class TileKey(val x: Int, val z: Int) : Comparable<TileKey> {
    /** The tree visits NW, NE, SW, SE recursively, rather than whole rows at a time. */
    override fun compareTo(other: TileKey): Int {
        val x = MapTree.unsignedX(this)
        val z = MapTree.unsignedZ(this)
        val otherX = MapTree.unsignedX(other)
        val otherZ = MapTree.unsignedZ(other)
        val bit = Integer.highestOneBit((x xor otherX) or (z xor otherZ))
        return if ((z xor otherZ) and bit != 0) (z and bit).compareTo(otherZ and bit)
        else (x and bit).compareTo(otherX and bit)
    }
}
