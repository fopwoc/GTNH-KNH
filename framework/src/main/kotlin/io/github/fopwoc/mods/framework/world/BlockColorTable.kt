package io.github.fopwoc.mods.framework.world

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * The color every block had the first time this map saw it, keyed by registry name and metadata.
 *
 * Scans read colors from here, never from the live textures, so a resource pack change cannot make
 * an unchanged world look changed: known blocks keep their recorded color forever, and only blocks
 * seen for the first time (a new mod, a new meta) take the current pack's color. The table travels
 * with the map data as a sorted, append-only `blocks.tsv` (`mod:block:meta<TAB>AARRGGBB`), so git
 * merges appends from several machines on its own; leftover conflict markers are skipped and the
 * first color for a key wins, so every machine converges on the same table.
 */
class BlockColorTable private constructor(private val colors: ConcurrentHashMap<String, Int>) {
    @Volatile private var dirty = false

    val size: Int
        get() = colors.size

    val isDirty: Boolean
        get() = dirty

    /** Recorded color of a block, or [record]'s result the first time, remembered from then on. */
    fun colorOf(name: String, meta: Int, record: () -> Int): Int {
        val key = key(name, meta)
        colors[key]?.let {
            return it
        }
        val color = record()
        if (colors.putIfAbsent(key, color) == null) dirty = true
        return colors.getValue(key)
    }

    fun all(): Collection<Int> = colors.values

    /** Writes the table when it grew; safe to call often. */
    fun saveIfDirty(file: Path) {
        if (!dirty) return
        dirty = false
        Files.createDirectories(file.parent)
        val temporary = Files.createTempFile(file.parent, ".blocks-", ".tmp")
        try {
            Files.newBufferedWriter(temporary).use { out ->
                for ((key, color) in colors.toSortedMap()) {
                    out.write(key)
                    out.write("\t")
                    out.write("%08X".format(color))
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
        const val FILE_NAME = "blocks.tsv"

        fun empty(): BlockColorTable = BlockColorTable(ConcurrentHashMap())

        fun load(file: Path): BlockColorTable {
            if (!Files.isRegularFile(file)) return empty()
            val colors = ConcurrentHashMap<String, Int>()
            Files.newBufferedReader(file).useLines { lines ->
                lines.mapNotNull(::parseLine).forEach { (key, color) ->
                    colors.putIfAbsent(key, color)
                }
            }
            return BlockColorTable(colors)
        }

        /** One `key<TAB>AARRGGBB` line; blanks, comments, conflict markers and junk are null. */
        private fun parseLine(line: String): Pair<String, Int>? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || isConflictMarker(trimmed))
                return null
            val tab = trimmed.indexOf('\t')
            if (tab <= 0) return null
            val color = trimmed.substring(tab + 1).trim().toLongOrNull(16) ?: return null
            return trimmed.substring(0, tab) to color.toInt()
        }

        private fun isConflictMarker(line: String) =
            line.startsWith("<<<<<<<") || line.startsWith("=======") || line.startsWith(">>>>>>>")

        private fun key(name: String, meta: Int) = "$name:${meta and 15}"
    }
}
