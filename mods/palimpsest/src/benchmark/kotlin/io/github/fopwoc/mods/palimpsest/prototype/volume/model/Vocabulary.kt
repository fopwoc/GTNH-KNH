package io.github.fopwoc.mods.palimpsest.prototype.volume.model

/**
 * Stable block identities shared by every save read in one run: `name:meta`, or `name@identity` for
 * tile-backed blocks whose look lives in the tile entity (GT ores and machines). Id 0 is air. Not
 * thread-safe; saves are parsed on one thread.
 */
class Vocabulary {
    private val ids = HashMap<String, Int>().apply { put(AIR, 0) }
    private val keys = arrayListOf(AIR)
    private val kinds = arrayListOf(BlockKind.AIR)

    val size: Int
        get() = keys.size

    fun id(key: String): Int =
        ids.getOrPut(key) {
            keys += key
            kinds += BlockKind.of(key.substringBefore('@').substringBeforeLast(':'))
            keys.size - 1
        }

    fun key(id: Int): String = keys[id]

    fun kind(id: Int): BlockKind = kinds[id]

    /** A snapshot indexed by id, for lock-free reads while scanning on several threads. */
    fun kinds(): Array<BlockKind> = kinds.toTypedArray()

    companion object {
        const val AIR = "minecraft:air"
    }
}
