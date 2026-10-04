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
     * Id of a block, assigning one and freezing [color] (0xRRGGBB) and [tint] on first sight. A
     * transparent block is [nothing] and never recorded.
     */
    fun idOf(key: String, color: Int, tint: Int): Int = own.idOf(key, color, tint)

    /** Id of a block already in the vocabulary, or [nothing]. */
    fun idOf(key: String): Int = own.idOf(key)

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
