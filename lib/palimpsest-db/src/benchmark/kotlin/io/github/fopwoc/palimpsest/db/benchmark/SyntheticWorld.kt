package io.github.fopwoc.palimpsest.db.benchmark

import io.github.fopwoc.palimpsest.db.Biomes
import io.github.fopwoc.palimpsest.db.BlockVocabulary
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.SectionBlocks
import kotlin.math.abs

/**
 * A 1.7.10-shaped world close enough to GTNH terrain for storage statistics: rolling hills over
 * stone, caves, GregTech-style ore veins in 3×3-chunk cells, water below sea level, trees with
 * leaves, grass and flowers, and biome patches. Deterministic per seed.
 */
class SyntheticWorld(seed: Long, vocabulary: BlockVocabulary) {
    private val noise = Noise(seed)

    val stone = vocabulary.id("minecraft:stone:0").raw
    val leaves = vocabulary.id("minecraft:leaves:0").raw
    val decayingLeaves = vocabulary.id("minecraft:leaves:8").raw
    val planks = vocabulary.id("minecraft:planks:0").raw
    val cobblestone = vocabulary.id("minecraft:cobblestone:0").raw
    val glass = vocabulary.id("minecraft:glass:0").raw
    private val bedrock = vocabulary.id("minecraft:bedrock:0").raw
    private val dirt = vocabulary.id("minecraft:dirt:0").raw
    private val grass = vocabulary.id("minecraft:grass:0").raw
    private val sand = vocabulary.id("minecraft:sand:0").raw
    private val gravel = vocabulary.id("minecraft:gravel:0").raw
    private val water = vocabulary.id("minecraft:water:0").raw
    private val log = vocabulary.id("minecraft:log:0").raw
    private val tallGrass = vocabulary.id("minecraft:tallgrass:1").raw
    private val flower = vocabulary.id("minecraft:red_flower:0").raw
    private val coal = vocabulary.id("minecraft:coal_ore:0").raw
    private val iron = vocabulary.id("minecraft:iron_ore:0").raw
    private val gtOres =
        List(VEIN_TYPES * ORES_PER_VEIN) { vocabulary.id("gregtech:gt.blockores@${1000 + it}").raw }

    /** The chunk's 16 sections as block id raws in YZX order; null for all-air sections. */
    fun sections(pos: ChunkPos): Array<IntArray?> {
        val sections = Array(SECTIONS) { IntArray(SectionBlocks.VOLUME) }
        val cellX = Math.floorDiv(pos.x, 3)
        val cellZ = Math.floorDiv(pos.z, 3)
        val vein = (noise.hash(cellX, -1, cellZ) * VEIN_TYPES).toInt()
        val veinY = 12 + (noise.hash(cellX, -2, cellZ) * 40).toInt()
        for (z in 0 until 16) for (x in 0 until 16) {
            val wx = pos.x * 16 + x
            val wz = pos.z * 16 + z
            val height = height(wx, wz)
            for (y in 0..maxOf(height, SEA_LEVEL)) {
                val block =
                    when {
                        y == 0 -> bedrock
                        y > height -> water
                        y == height -> if (height < SEA_LEVEL + 1) sand else grass
                        y > height - 4 -> if (height < SEA_LEVEL + 1) sand else dirt
                        y in 5 until height - 6 &&
                            noise.at(wx / 24.0, y / 14.0, wz / 24.0) > 0.73 -> 0
                        abs(y - veinY) < 4 && noise.hash(wx, y, wz) < 0.3 ->
                            gtOres[
                                vein * ORES_PER_VEIN +
                                    (noise.hash(wz, y, wx) * ORES_PER_VEIN).toInt()]
                        noise.hash(wx, y + 7, wz) < 0.012 -> coal
                        noise.hash(wx, y + 11, wz) < 0.006 -> iron
                        noise.hash(wx, y + 13, wz) < 0.01 -> gravel
                        else -> stone
                    }
                sections[y shr 4][SectionBlocks.index(x, y and 15, z)] = block
            }
            if (height <= SEA_LEVEL || height + 1 >= HEIGHT) continue
            val roll = noise.hash(wx, -3, wz)
            val above = sections[(height + 1) shr 4]
            when {
                roll < 0.12 -> above[SectionBlocks.index(x, (height + 1) and 15, z)] = tallGrass
                roll < 0.14 -> above[SectionBlocks.index(x, (height + 1) and 15, z)] = flower
            }
        }
        plantTrees(pos, sections)
        return Array(SECTIONS) { index ->
            sections[index].takeUnless { section -> section.all { it == 0 } }
        }
    }

    fun biomes(pos: ChunkPos): IntArray =
        IntArray(Biomes.Columns.COLUMNS) { at ->
            val wx = pos.x * 16 + (at and 15)
            val wz = pos.z * 16 + (at shr 4)
            BIOMES[
                (noise.at(wx / 96.0, wz / 96.0) * BIOMES.size)
                    .toInt()
                    .coerceAtMost(BIOMES.size - 1)]
        }

    fun observe(
        pos: ChunkPos,
        sections: Array<IntArray?>,
        biomes: IntArray = biomes(pos),
    ): ChunkObservation =
        ChunkObservation(
            pos,
            0,
            sections.map { it?.let(SectionBlocks::of) },
            Biomes.Columns(biomes),
        )

    fun height(wx: Int, wz: Int): Int =
        (60 + 18 * noise.at(wx / 80.0, wz / 80.0) + 6 * noise.at(wx / 20.0, wz / 20.0)).toInt()

    private fun plantTrees(pos: ChunkPos, sections: Array<IntArray>) {
        for (z in 2 until 14) for (x in 2 until 14) {
            val wx = pos.x * 16 + x
            val wz = pos.z * 16 + z
            val height = height(wx, wz)
            if (height <= SEA_LEVEL || height + 8 >= HEIGHT || noise.hash(wx, -4, wz) >= 0.012)
                continue
            fun set(dx: Int, y: Int, dz: Int, block: Int) {
                val section = sections[y shr 4]
                val at = SectionBlocks.index(x + dx, y and 15, z + dz)
                if (block == log || section[at] == 0) section[at] = block
            }
            for (dy in 3..6) {
                val radius = if (dy >= 5) 1 else 2
                for (dz in -radius..radius) for (dx in -radius..radius) {
                    if (abs(dx) + abs(dz) <= radius + 1) set(dx, height + dy, dz, leaves)
                }
            }
            for (dy in 1..5) set(0, height + dy, 0, log)
        }
    }

    companion object {
        const val SECTIONS = 16
        const val HEIGHT = SECTIONS * 16
        const val SEA_LEVEL = 62
        private const val VEIN_TYPES = 12
        private const val ORES_PER_VEIN = 4
        private val BIOMES = intArrayOf(1, 4, 2, 3, 7, 21)
    }
}
