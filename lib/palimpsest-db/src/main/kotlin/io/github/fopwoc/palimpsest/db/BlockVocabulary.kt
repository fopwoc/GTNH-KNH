package io.github.fopwoc.palimpsest.db

/**
 * The world's block identities, append-only: an identity keeps its [BlockId] forever. The mod
 * normalizes identities (`minecraft:leaves:0`, `gregtech:gt.blockores@1043`); the database stores
 * them as opaque strings. Thread-safe and cheap enough to call from the game thread through a
 * cache.
 */
interface BlockVocabulary {
    fun id(identity: String): BlockId

    fun identity(id: BlockId): String

    val size: Int
}
