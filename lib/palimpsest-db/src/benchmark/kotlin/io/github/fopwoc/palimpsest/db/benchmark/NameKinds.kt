package io.github.fopwoc.palimpsest.db.benchmark

import io.github.fopwoc.palimpsest.db.BlockKind
import io.github.fopwoc.palimpsest.db.BlockKinds

/** Block kinds guessed from 1.7.10 identities, standing in for the mod's color mapping offline. */
object NameKinds : BlockKinds {
    private val decorations =
        listOf(
            "tallgrass",
            "flower",
            "torch",
            "sapling",
            "reeds",
            "deadbush",
            "double_plant",
            "vine",
            "waterlily",
            "snow_layer",
            "carpet",
            "rail",
            "button",
            "lever",
            "sign",
            "ladder",
            "mushroom",
            "wheat",
            "carrots",
            "potatoes",
            "web",
            "fence",
            "pane",
            "slab",
            "redstone_wire",
            "pressure_plate",
        )

    override fun kind(identity: String): BlockKind {
        val name = identity.substringBefore('@').substringBeforeLast(':').lowercase()
        return when {
            name.endsWith(":air") -> BlockKind.AIR
            name.endsWith("water") -> BlockKind.WATER
            name.endsWith("lava") || name.contains("fluid") -> BlockKind.LIQUID
            name.endsWith(":glass") || name.contains("stained_glass") -> BlockKind.TRANSPARENT
            decorations.any { name.contains(it) } -> BlockKind.DECORATION
            else -> BlockKind.SOLID
        }
    }
}
