package io.github.fopwoc.mods.palimpsest.prototype.volume.save

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.ChunkVolume
import io.github.fopwoc.mods.palimpsest.prototype.volume.model.Vocabulary
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * A 1.7.10 save (vanilla or GTNH with NotEnoughIDs), read offline into [ChunkVolume]s. Numeric
 * block ids go through the save's own FML registry, so two saves of one world, or two worlds, land
 * in the same [Vocabulary]. GT ores and machines take their identity from the tile entity, the way
 * the in-game scanner tells one ore or machine from another.
 */
class LegacySave(private val directory: Path, private val vocabulary: Vocabulary) {
    private val level =
        GZIPInputStream(Files.newInputStream(directory.resolve("level.dat"))).use {
            Nbt.read(it.readBytes())
        }

    private val names: Map<Int, String> =
        level
            .compound("FML")
            .compounds("ItemData")
            .filter { (it["K"] as String).startsWith('\u0001') }
            .associate { (it["V"] as Int) to (it["K"] as String).substring(1) }

    /** World spawn in block coordinates. */
    val spawn: Pair<Int, Int> =
        level.compound("Data").let { (it["SpawnX"] as Int) to (it["SpawnZ"] as Int) }

    /** Region files of a dimension: `region` for the overworld, `DIM-1/region` for the Nether. */
    fun regions(dimension: String = "region"): List<Path> =
        directory.resolve(dimension).listDirectoryEntries("*.mca").sortedBy { it.name }

    /** A chunk with how long players stayed near it, in game ticks. */
    class Saved(val key: TileKey, val volume: ChunkVolume, val inhabitedTicks: Long)

    fun chunks(region: Path): Sequence<Saved> =
        RegionFile(region).chunks().mapNotNull { chunk ->
            val level = chunk.tag.compound("Level")
            if (level["TerrainPopulated"] != 1.toByte()) return@mapNotNull null
            Saved(TileKey(chunk.x, chunk.z), volume(level), level["InhabitedTime"] as? Long ?: 0)
        }

    fun volumes(region: Path): Sequence<Pair<TileKey, ChunkVolume>> =
        chunks(region).map { it.key to it.volume }

    private fun volume(level: Map<String, Any?>): ChunkVolume {
        val identities = HashMap<Int, String>()
        for (entity in level.compounds("TileEntities")) {
            val identity = (entity["m"] ?: entity["mID"])?.toString() ?: continue
            val x = (entity["x"] as Int) and 15
            val z = (entity["z"] as Int) and 15
            identities[((entity["y"] as Int) shl 8) or (z shl 4) or x] = identity
        }
        val sections = arrayOfNulls<IntArray>(ChunkVolume.SECTIONS)
        for (section in level.compounds("Sections")) {
            val y = (section["Y"] as Byte).toInt()
            sections[y] = blocks(section, y, identities).takeUnless { ids -> ids.all { it == 0 } }
        }
        val biomes =
            (level["Biomes"] as? ByteArray)?.let { bytes ->
                IntArray(ChunkVolume.COLUMNS) { bytes[it].toInt() and 0xFF }
            } ?: IntArray(ChunkVolume.COLUMNS)
        return ChunkVolume(sections, biomes)
    }

    private fun blocks(section: Map<String, Any?>, y: Int, identities: Map<Int, String>): IntArray {
        val ids: IntArray
        val metas: IntArray
        if (section["Blocks16"] != null) {
            val blocks = ByteBuffer.wrap(section["Blocks16"] as ByteArray).asShortBuffer()
            val data = ByteBuffer.wrap(section["Data16"] as ByteArray).asShortBuffer()
            ids = IntArray(ChunkVolume.SECTION_BLOCKS) { blocks[it].toInt() and 0xFFFF }
            metas = IntArray(ChunkVolume.SECTION_BLOCKS) { data[it].toInt() and 0xFFFF }
        } else {
            val blocks = section["Blocks"] as ByteArray
            val add = section["Add"] as ByteArray?
            val data = section["Data"] as ByteArray
            ids =
                IntArray(ChunkVolume.SECTION_BLOCKS) {
                    (blocks[it].toInt() and 0xFF) or ((add?.let { a -> nibble(a, it) } ?: 0) shl 8)
                }
            metas = IntArray(ChunkVolume.SECTION_BLOCKS) { nibble(data, it) }
        }
        return IntArray(ChunkVolume.SECTION_BLOCKS) { at ->
            val id = ids[at]
            if (id == 0) return@IntArray 0
            val name = names[id] ?: "unknown:$id"
            val identity = identities[(y shl 12) or at]
            vocabulary.id(if (identity != null) "$name@$identity" else "$name:${metas[at]}")
        }
    }

    private fun nibble(bytes: ByteArray, index: Int): Int =
        (bytes[index shr 1].toInt() shr ((index and 1) * 4)) and 15
}
