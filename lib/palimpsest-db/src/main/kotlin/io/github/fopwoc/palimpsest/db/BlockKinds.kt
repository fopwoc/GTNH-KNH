package io.github.fopwoc.palimpsest.db

/** The display mapping's kind for a block identity, supplied by the mod at runtime. */
fun interface BlockKinds {
    fun kind(identity: String): BlockKind
}
