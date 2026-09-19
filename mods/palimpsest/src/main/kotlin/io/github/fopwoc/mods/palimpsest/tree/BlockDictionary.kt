package io.github.fopwoc.mods.palimpsest.tree

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * One machine's block vocabulary: `mod:block:meta<TAB>id<TAB>RRGGBB<TAB>g|f|-` (grass-tinted,
 * foliage-tinted, plain), ids from 1 in order
 * of first sight, the color and tint flag frozen the moment the block was first seen so a resource
 * pack change never repaints old history. Only the owning machine appends to its file; other
 * machines read it to translate that machine's records.
 */
class BlockDictionary private constructor(val machineId: Int, entries: List<Entry>) {
    /** Which biome colour multiplies the block: 0 none, 1 grass, 2 foliage. */
    class Entry(val key: String, val id: Int, val color: Int, val tint: Int)

    private val byKey = HashMap<String, Entry>()
    @Volatile private var byId: Array<Entry?> = arrayOfNulls(entries.size + 1)
    @Volatile private var dirty = false

    init {
        for (entry in entries) put(entry)
    }

    val size: Int
        get() = byKey.size

    val isDirty: Boolean
        get() = dirty

    private fun put(entry: Entry) {
        require(entry.id >= 1)
        byKey[entry.key] = entry
        if (entry.id >= byId.size) byId = byId.copyOf(maxOf(byId.size * 2, entry.id + 1))
        byId[entry.id] = entry
    }

    fun entry(id: Int): Entry? = byId.let { if (id in it.indices) it[id] else null }

    fun idOf(key: String): Int = byKey[key]?.id ?: 0

    /** The block's id, assigning the next one and freezing its color the first time. */
    @Synchronized
    fun idOf(key: String, color: Int, tint: Int): Int {
        byKey[key]?.let {
            return it.id
        }
        val entry = Entry(key, byKey.size + 1, color and 0xFFFFFF, tint)
        put(entry)
        dirty = true
        return entry.id
    }

    @Synchronized
    fun saveIfDirty(file: Path) {
        if (!dirty) return
        dirty = false
        Files.createDirectories(file.parent)
        val temporary = Files.createTempFile(file.parent, ".blocks-", ".tmp")
        try {
            Files.newBufferedWriter(temporary).use { out ->
                for (entry in byId) {
                    if (entry == null) continue
                    out.write(entry.key)
                    out.write("\t")
                    out.write(entry.id.toString())
                    out.write("\t")
                    out.write("%06X".format(entry.color))
                    out.write("\t")
                    out.write(FLAGS[entry.tint].toString())
                    out.write("\n")
                }
            }
            Files.move(
                temporary,
                file,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        const val PREFIX = "blocks."
        private const val FLAGS = "-gf"
        const val SUFFIX = ".tsv"

        fun fileName(machineId: Int): String = "$PREFIX${MachineId.hex(machineId)}$SUFFIX"

        fun machineOf(fileName: String): Int? =
            fileName
                .takeIf { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }
                ?.removePrefix(PREFIX)
                ?.removeSuffix(SUFFIX)
                ?.toLongOrNull(16)
                ?.toInt()

        fun load(file: Path, machineId: Int): BlockDictionary {
            if (!Files.isRegularFile(file)) return BlockDictionary(machineId, emptyList())
            val entries =
                Files.readAllLines(file).mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size != 4) return@mapNotNull null
                    val id = parts[1].toIntOrNull() ?: return@mapNotNull null
                    val color = parts[2].toIntOrNull(16) ?: return@mapNotNull null
                    // "t" is the old grass flag.
                    Entry(parts[0], id, color, if (parts[3] == "t") 1 else FLAGS.indexOf(parts[3].firstOrNull() ?: '-').coerceAtLeast(0))
                }
            return BlockDictionary(machineId, entries)
        }
    }
}
