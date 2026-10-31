package io.github.fopwoc.mods.palimpsest.map

/** A rectangle of ground in block coordinates, as much as a view shows. */
data class GroundArea(val minX: Double, val minZ: Double, val maxX: Double, val maxZ: Double) {
    init {
        require(minX <= maxX && minZ <= maxZ)
    }

    val centerX: Double
        get() = (minX + maxX) / 2

    val centerZ: Double
        get() = (minZ + maxZ) / 2
}
