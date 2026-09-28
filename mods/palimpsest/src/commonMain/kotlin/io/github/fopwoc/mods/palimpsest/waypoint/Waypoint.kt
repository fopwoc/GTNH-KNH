package io.github.fopwoc.mods.palimpsest.waypoint

import io.github.fopwoc.mods.framework.minecraft.ItemId
import java.util.UUID

/** A player-owned place in one world and dimension. */
data class Waypoint(
    val id: UUID,
    val name: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val icon: ItemId,
    val tracked: Boolean = true,
) {
    init {
        require(name.isNotBlank()) { "Waypoint name cannot be blank" }
    }
}
