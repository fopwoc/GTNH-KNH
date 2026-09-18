package io.github.fopwoc.mods.framework.world

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * The color every block had the first time this map saw it, keyed by registry name and metadata.
 *
 * Scans read colors from here, never from the live textures, so a resource pack change cannot make
 * an unchanged world look changed: known blocks keep their recorded color forever, and only blocks
 * seen for the first time take the current pack's color. The table travels with the map data,
 * append-only, so every machine encodes a block the same way.
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
            DataOutputStream(Files.newOutputStream(temporary).buffered()).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                val snapshot = colors.toSortedMap()
                out.writeInt(snapshot.size)
                for ((key, color) in snapshot) {
                    out.writeUTF(key)
                    out.writeInt(color)
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
        private const val MAGIC = 0x424C4B43 // BLKC
        private const val VERSION = 1

        fun empty(): BlockColorTable = BlockColorTable(ConcurrentHashMap())

        fun load(file: Path): BlockColorTable {
            if (!Files.isRegularFile(file)) return empty()
            DataInputStream(Files.newInputStream(file).buffered()).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                    throw IOException("Unsupported block color table $file")
                }
                val count = input.readInt()
                if (count < 0) throw IOException("Invalid block color table $file")
                val colors = ConcurrentHashMap<String, Int>(count * 2)
                repeat(count) { colors[input.readUTF()] = input.readInt() }
                return BlockColorTable(colors)
            }
        }

        private fun key(name: String, meta: Int) = "$name:${meta and 15}"
    }
}
