package io.github.fopwoc.mods.palimpsest.tree

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The map's block colours: `identity<TAB>id<TAB>RRGGBB<TAB>g|f|-[<TAB>?]` (grass-tinted,
 * foliage-tinted, plain; `?` for a guess), ids from 1 in order of first sight, the color and tint
 * flag frozen the moment the block was first seen so a resource pack change never repaints old
 * history. A guess, made from a block's look without a world to place it in, misses the biome tint
 * among others, so the first real sight replaces it under the same id.
 */
class BlockDictionary private constructor(entries: List<Entry>) {
    /** Which biome colour multiplies the block: 0 none, 1 grass, 2 foliage. */
    class Entry(val key: String, val id: Int, val color: Int, val tint: Int, val guessed: Boolean)

    private val byKey = HashMap<String, Entry>()
    private var lastId = 0
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
        byKey.putIfAbsent(entry.key, entry)
        lastId = maxOf(lastId, entry.id)
        if (entry.id >= byId.size) byId = byId.copyOf(maxOf(byId.size * 2, entry.id + 1))
        byId[entry.id] = entry
    }

    fun entry(id: Int): Entry? = byId.let { if (id in it.indices) it[id] else null }

    /** Id of a block seen for real, or 0; a guess does not count. */
    fun idOf(key: String): Int = byKey[key]?.takeUnless { it.guessed }?.id ?: 0

    /**
     * The block's id, assigning the next one and freezing its color the first time it is seen for
     * real; a guess made before is replaced.
     */
    @Synchronized
    fun idOf(key: String, color: Int, tint: Int): Int {
        val known = byKey[key] ?: return assign(key, color, tint, guessed = false)
        if (!known.guessed) return known.id
        require(tint in FLAGS.indices)
        val entry = Entry(key, known.id, color and 0xFFFFFF, tint, guessed = false)
        byKey[key] = entry
        byId[entry.id] = entry
        dirty = true
        return entry.id
    }

    /** The block's id, assigning the next one with a guessed color if it has none yet. */
    @Synchronized
    fun guess(key: String, color: Int, tint: Int): Int =
        byKey[key]?.id ?: assign(key, color, tint, guessed = true)

    private fun assign(key: String, color: Int, tint: Int, guessed: Boolean): Int {
        require(lastId < 0xFFFF) { "Block vocabulary exceeds sixteen-bit IDs" }
        require(tint in FLAGS.indices)
        val entry = Entry(key, lastId + 1, color and 0xFFFFFF, tint, guessed)
        put(entry)
        dirty = true
        return entry.id
    }

    @Synchronized
    fun saveIfDirty(file: Path) {
        if (!dirty) return
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
                    if (entry.guessed) out.write("\t$GUESSED")
                    out.write("\n")
                }
            }
            Files.move(
                temporary,
                file,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            dirty = false
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        private const val FLAGS = "-gf"
        private const val GUESSED = "?"

        fun load(file: Path): BlockDictionary {
            if (!Files.isRegularFile(file)) return BlockDictionary(emptyList())
            val entries =
                Files.readAllLines(file).mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size !in 4..5) return@mapNotNull null
                    val id = parts[1].toIntOrNull() ?: return@mapNotNull null
                    val color = parts[2].toIntOrNull(16) ?: return@mapNotNull null
                    Entry(
                        parts[0],
                        id,
                        color,
                        FLAGS.indexOf(parts[3].firstOrNull() ?: '-').coerceAtLeast(0),
                        guessed = parts.getOrNull(4) == GUESSED,
                    )
                }
            return BlockDictionary(entries)
        }
    }
}
