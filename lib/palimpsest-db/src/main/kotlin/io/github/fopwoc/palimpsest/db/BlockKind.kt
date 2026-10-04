package io.github.fopwoc.palimpsest.db

/** What a block is to the map's scan rules; part of the display mapping, not of history. */
enum class BlockKind {
    AIR,
    TRANSPARENT,
    WATER,
    LIQUID,
    DECORATION,
    SOLID,
}
