package io.github.fopwoc.palimpsest.db

/**
 * The display mapping's kind for a block identity, supplied by the mod at runtime. Called from the
 * database's background threads, several at once, once per identity per session; it must be
 * thread-safe, must not touch the game, and must answer the same for an identity all session.
 */
fun interface BlockKinds {
    fun kind(identity: String): BlockKind
}
