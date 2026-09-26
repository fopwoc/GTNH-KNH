package io.github.fopwoc.mods.framework.client

/** An entity near the local player as the client sees it right now; meant to be drawn, not kept. */
data class EntitySighting(
    /** The entity's network id, stable while it stays loaded. */
    val id: Int,
    val kind: EntityKind,
    val x: Double,
    val y: Double,
    val z: Double,
)

enum class EntityKind {
    /** A dropped item stack. */
    ITEM,

    /** A mob that attacks on sight. */
    HOSTILE,

    /** Any other mob: animals, villagers, golems, fish. */
    PASSIVE,

    /** Another player. */
    PLAYER,
}
