package io.github.fopwoc.mods.palimpsest.map

/**
 * One value per pixel that the map records, such as the palette entry or the biome id. Storage is
 * byte planes; a channel wider than a byte is kept as several planes, low byte first, each in its
 * own history so a plane that rarely changes (the high byte of the biome id) stays nearly free.
 */
class MapChannel(val name: String, val bytes: Int = 1) {
    /** Largest value the channel can hold. */
    val maxValue: Int = (1 shl (bytes * Byte.SIZE_BITS)) - 1

    /** Directory names of the planes, low byte first. */
    val planes: List<String> = List(bytes) { if (it == 0) name else "$name.$it" }

    init {
        require(name.isNotBlank() && '/' !in name)
        require(bytes in 1..2)
    }

    override fun toString(): String = "$name(${bytes * Byte.SIZE_BITS}-bit)"

    companion object {
        val COLORS = MapChannel("colors")
        val BIOMES = MapChannel("biomes", bytes = 2)
    }
}
