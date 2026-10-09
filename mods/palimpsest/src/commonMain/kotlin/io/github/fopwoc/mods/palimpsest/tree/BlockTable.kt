package io.github.fopwoc.mods.palimpsest.tree

import java.nio.file.Path

/**
 * The map's block colours by id, kept in [file]: a local cache next to the history's indexes, since
 * every machine sees blocks with its own resource packs.
 */
class BlockTable(private val file: Path) {
    private val own = BlockDictionary.load(file)

    val size: Int
        get() = own.size

    /** Nothing here; the map looks through it. */
    val nothing: Int
        get() = 0

    /**
     * Id of a block seen in the world, assigning one and freezing [color] (0xRRGGBB) and [tint] on
     * first sight, over a [guess] if there was one. A transparent block is [nothing] and never
     * recorded.
     */
    fun idOf(key: String, color: Int, tint: Int): Int = own.idOf(key, color, tint)

    /** Id of a block already seen for real, or [nothing]; a guess does not count. */
    fun idOf(key: String): Int = own.idOf(key)

    /**
     * Id of a block known only from its look without a world, like history from another session:
     * its colour is a guess until [idOf] with the real look replaces it.
     */
    fun guess(key: String, color: Int, tint: Int): Int = own.guess(key, color, tint)

    fun color(id: Int): Int = own.entry(id)?.color ?: UNKNOWN_COLOR

    /** 0 none, 1 grass colour, 2 foliage colour. */
    fun tint(id: Int): Int = own.entry(id)?.tint ?: 0

    fun key(id: Int): String? = own.entry(id)?.key

    fun saveIfDirty() = own.saveIfDirty(file)

    companion object {
        /** Magenta, so a record naming an id the vocabulary lost is visible, not invisible. */
        const val UNKNOWN_COLOR = 0xFF00FF
    }
}
