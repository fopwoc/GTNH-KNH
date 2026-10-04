package io.github.fopwoc.mods.palimpsest.prototype.volume.model

/**
 * How the top-down scanner treats a block. In game the material decides; offline only the registry
 * name is known, so [of] guesses from it. Both stores in a comparison share the guess.
 */
enum class BlockKind {
    AIR,
    TRANSPARENT,
    WATER,
    LIQUID,
    DECORATION,
    SOLID;

    companion object {
        private val transparent =
            listOf(
                "glass",
                "pane",
                "torch",
                "tallgrass",
                "deadbush",
                "vine",
                "web",
                "fire",
                "ladder",
                "redstone_wire",
                "tripwire",
                "lever",
                "button",
                "glow_lichen",
            )
        private val decoration =
            listOf(
                "flower",
                "sapling",
                "slab",
                "wheat",
                "carrots",
                "potatoes",
                "reeds",
                "brown_mushroom",
                "red_mushroom",
                "carpet",
                "rail",
                "pressure_plate",
                "sign",
                "flower_pot",
                "double_plant",
                "waterlily",
                "snow_layer",
                "crop",
                "cave_vines",
            )

        fun of(name: String): BlockKind {
            val path = name.substringAfter(':').lowercase()
            return when {
                name == "minecraft:air" -> AIR
                path == "water" || path == "flowing_water" -> WATER
                path == "lava" || path == "flowing_lava" || "fluid" in path || "molten" in path ->
                    LIQUID
                transparent.any { it in path } -> TRANSPARENT
                decoration.any { it in path } && "mushroom_block" !in path -> DECORATION
                else -> SOLID
            }
        }
    }
}
