package io.github.fopwoc.mods.palimpsest.tree

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/**
 * The map's block vocabulary as this machine sees it: its own [BlockDictionary] is the id space
 * every record in memory uses, and the other machines' dictionaries in the directory translate
 * their records into it on read. Colors are frozen per dictionary, so what a machine saw is what
 * its observations are drawn with, on every machine.
 */
class BlockTable(private val directory: Path, val machineId: Int) {
    private val own = BlockDictionary.load(directory.resolve(BlockDictionary.fileName(machineId)), machineId)
    private val foreign = HashMap<Int, BlockDictionary>()
    private val translations = HashMap<Long, Int>()

    init {
        Files.createDirectories(directory)
        Files.list(directory).use { files ->
            for (file in files) {
                val machine = BlockDictionary.machineOf(file.name) ?: continue
                if (machine != machineId) foreign[machine] = BlockDictionary.load(file, machine)
            }
        }
    }

    val size: Int
        get() = own.size

    /** Nothing here; the map looks through it. */
    val nothing: Int
        get() = 0

    /**
     * Id of a block, assigning one and freezing [color] (0xRRGGBB) and [tintable] on first sight.
     * A transparent block is [nothing] and never recorded.
     */
    fun idOf(key: String, color: Int, tintable: Boolean): Int = own.idOf(key, color, tintable)

    /** Id of a block already in the vocabulary, or [nothing]. */
    fun idOf(key: String): Int = own.idOf(key)

    fun color(id: Int): Int = own.entry(id)?.color ?: UNKNOWN_COLOR

    fun isTintable(id: Int): Boolean = own.entry(id)?.tintable ?: false

    fun key(id: Int): String? = own.entry(id)?.key

    /** Another machine's id in this machine's id space, adopting its frozen color if unseen here. */
    fun translate(machine: Int, id: Int): Int {
        if (machine == machineId || id == 0) return id
        val packed = (machine.toLong() shl 32) or id.toLong()
        synchronized(translations) {
            translations[packed]?.let {
                return it
            }
            val entry = foreign[machine]?.entry(id) ?: return 0
            return own.idOf(entry.key, entry.color, entry.tintable).also { translations[packed] = it }
        }
    }

    fun saveIfDirty() = own.saveIfDirty(directory.resolve(BlockDictionary.fileName(machineId)))

    companion object {
        /** Magenta, so a record naming an id the vocabulary lost is visible, not invisible. */
        const val UNKNOWN_COLOR = 0xFF00FF
    }
}
