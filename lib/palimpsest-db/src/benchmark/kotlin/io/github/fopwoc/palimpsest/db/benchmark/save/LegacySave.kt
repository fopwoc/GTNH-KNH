package io.github.fopwoc.palimpsest.db.benchmark.save

import io.github.fopwoc.palimpsest.db.Biomes
import io.github.fopwoc.palimpsest.db.BlockVocabulary
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.SectionBlocks
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * A 1.7.10 save (vanilla or GTNH with NotEnoughIDs), read offline into [ChunkObservation]s. Numeric
 * block ids go through the save's own FML registry. GT ores and machines take their identity from
 * the tile entity, the way the in-game scanner tells one ore or machine from another.
 */
class LegacySave(private val directory: Path, private val vocabulary: BlockVocabulary) {
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

    /** Region files of a dimension: `region` for the overworld, `DIM-1/region` for the Nether. */
    fun regions(dimension: String = "region"): List<Path> =
        directory.resolve(dimension).listDirectoryEntries("*.mca").sortedBy { it.name }

    /** Populated chunks of one region file. */
    fun chunks(region: Path): List<ChunkObservation> =
        RegionFile(region)
            .chunks()
            .mapNotNull { chunk ->
                val level = chunk.tag.compound("Level")
                if (level["TerrainPopulated"] != 1.toByte()) null
                else observation(ChunkPos(chunk.x, chunk.z), level)
            }
            .toList()

    private fun observation(pos: ChunkPos, level: Map<String, Any?>): ChunkObservation {
        val identities = HashMap<Int, String>()
        for (entity in level.compounds("TileEntities")) {
            val identity = (entity["m"] ?: entity["mID"])?.toString() ?: continue
            val x = (entity["x"] as Int) and 15
            val z = (entity["z"] as Int) and 15
            identities[((entity["y"] as Int) shl 8) or (z shl 4) or x] = identity
        }
        val sections = arrayOfNulls<SectionBlocks>(SECTIONS)
        for (section in level.compounds("Sections")) {
            val y = (section["Y"] as Byte).toInt()
            if (y !in 0 until SECTIONS) continue
            val blocks = blocks(section, y, identities)
            if (blocks.any { it != 0 }) sections[y] = SectionBlocks.of(blocks)
        }
        val biomes =
            (level["Biomes"] as? ByteArray)?.let { bytes ->
                IntArray(Biomes.Columns.COLUMNS) { bytes[it].toInt() and 0xFF }
            } ?: IntArray(Biomes.Columns.COLUMNS)
        return ChunkObservation(pos, 0, sections.toList(), Biomes.Columns(biomes))
    }

    private fun blocks(section: Map<String, Any?>, y: Int, identities: Map<Int, String>): IntArray {
        val ids: IntArray
        val metas: IntArray
        if (section["Blocks16"] != null) {
            val blocks = ByteBuffer.wrap(section["Blocks16"] as ByteArray).asShortBuffer()
            val data = ByteBuffer.wrap(section["Data16"] as ByteArray).asShortBuffer()
            ids = IntArray(SectionBlocks.VOLUME) { blocks[it].toInt() and 0xFFFF }
            metas = IntArray(SectionBlocks.VOLUME) { data[it].toInt() and 0xFFFF }
        } else {
            val blocks = section["Blocks"] as ByteArray
            val add = section["Add"] as ByteArray?
            val data = section["Data"] as ByteArray
            ids =
                IntArray(SectionBlocks.VOLUME) {
                    (blocks[it].toInt() and 0xFF) or ((add?.let { a -> nibble(a, it) } ?: 0) shl 8)
                }
            metas = IntArray(SectionBlocks.VOLUME) { nibble(data, it) }
        }
        val cache = HashMap<Long, Int>()
        return IntArray(SectionBlocks.VOLUME) { at ->
            val id = ids[at]
            if (id == 0) return@IntArray 0
            val identity = identities[(y shl 12) or at]
            if (identity != null)
                return@IntArray vocabulary.id("${names[id] ?: "unknown:$id"}@$identity").raw
            cache.getOrPut((id.toLong() shl 16) or metas[at].toLong()) {
                vocabulary.id("${names[id] ?: "unknown:$id"}:${metas[at]}").raw
            }
        }
    }

    private fun nibble(bytes: ByteArray, index: Int): Int =
        (bytes[index shr 1].toInt() shr ((index and 1) * 4)) and 15

    private companion object {
        const val SECTIONS = 16
    }
}
