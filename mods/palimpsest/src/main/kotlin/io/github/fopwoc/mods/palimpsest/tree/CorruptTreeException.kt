package io.github.fopwoc.mods.palimpsest.tree

/** Structural damage in a segment or record; the store cannot trust the bytes it read. */
class CorruptTreeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
