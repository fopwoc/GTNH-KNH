package io.github.fopwoc.mods.palimpsest.tree

/** The map is already being written by another game process with the same machine id. */
class MapInUseException(message: String) : RuntimeException(message)
