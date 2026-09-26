package io.github.fopwoc.mods.palimpsest.config

/** What stays fixed on the minimap while the player turns. */
enum class MinimapRotation {
    /** The map, north up; the arrow turns. */
    NORTH_UP,

    /** The arrow, pointing up; the map turns under it. */
    PLAYER_UP,
}
