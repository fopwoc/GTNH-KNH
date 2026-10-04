package io.github.fopwoc.mods.palimpsest.prototype.volume

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.ChunkVolume
import io.github.fopwoc.mods.palimpsest.prototype.volume.storage.tree.VolumeTree
import io.github.fopwoc.mods.palimpsest.tree.Sample
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VolumeTreeTest {
    private fun terrain(random: Random): ChunkVolume =
        ChunkVolume(
            Array(ChunkVolume.SECTIONS) { section ->
                if (section > 4) null else IntArray(ChunkVolume.SECTION_BLOCKS) { if (random.nextInt(30) == 0) 7 else 1 + section }
            },
            IntArray(ChunkVolume.COLUMNS) { 3 },
        )

    private fun surfaceOf(volume: ChunkVolume, epoch: Long): TileRecord =
        TileRecord.build(epoch, { volume.block(it % 16, 70, it / 16) + 1 }, { 64 }, biome = { volume.biome(it % 16, it / 16) })

    @Test
    fun everyMomentReadsBackAndDiffsMatchBruteForce() {
        val directory = Files.createTempDirectory("volume-tree-test-")
        try {
            VolumeTree(directory, cacheEntries = 64).use { tree ->
                val random = Random(5)
                val world = HashMap<TileKey, ChunkVolume>()
                val oracle = HashMap<Long, Map<TileKey, ChunkVolume>>()
                var keys = List(12) { TileKey(it % 4, it / 4) }
                for (epoch in 1L..60L) {
                    if (epoch == 30L) keys = keys + TileKey(-700, 900) + TileKey(5000, -3)
                    val changed = HashMap<TileKey, ChunkVolume>()
                    for (key in keys.shuffled(random).take(if (epoch == 1L || epoch == 30L) keys.size else 3)) {
                        val before = world[key]
                        changed[key] = before?.edit {
                            repeat(random.nextInt(1, 40)) {
                                set(random.nextInt(16), random.nextInt(80), random.nextInt(16), random.nextInt(4))
                            }
                            if (random.nextInt(5) == 0) biome(0, 0, random.nextInt(30))
                        } ?: terrain(random)
                    }
                    world.putAll(changed)
                    tree.commit(epoch, changed, changed.mapValues { (_, v) -> surfaceOf(v, epoch) })
                    oracle[epoch] = HashMap(world)
                }
                for (epoch in 1L..60L) {
                    tree.clearCaches()
                    val root = tree.rootAt(epoch)!!
                    for ((key, expected) in oracle.getValue(epoch)) {
                        val ref = tree.leafRef(root, key)
                        val actual = tree.volume(ref)
                        for (section in 0 until ChunkVolume.SECTIONS)
                            assertContentEquals(expected.section(section), actual.section(section), "$key@$epoch s$section")
                        assertContentEquals(expected.biomes(), actual.biomes())
                        assertTrue(surfaceOf(expected, epoch).sameFacts(tree.summary(ref)))
                    }
                }
                for ((from, to) in listOf(1L to 2L, 3L to 40L, 29L to 30L, 10L to 60L, 59L to 60L)) {
                    val expected = oracle.getValue(to).filter { (key, volume) -> oracle.getValue(from)[key] !== volume }.keys
                    assertEquals(expected, tree.diff(from, to).map { it.key }.toSet(), "$from → $to")
                }
                val far = tree.samples(60, 1, 0, 0, 2)
                assertTrue(far.all { it != Sample.NONE.packed }, "explored squares have far-zoom samples")
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
