package io.github.fopwoc.mods.palimpsest.prototype.volume

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.ChunkVolume
import io.github.fopwoc.mods.palimpsest.prototype.volume.storage.VolumeStore
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VolumeStoreTest {
    private fun terrain(seed: Int): ChunkVolume {
        val random = Random(seed)
        val sections =
            Array(ChunkVolume.SECTIONS) { section ->
                if (section > 5) null
                else
                    IntArray(ChunkVolume.SECTION_BLOCKS) {
                        if (random.nextInt(50) == 0) 9 else 1 + section % 2
                    }
            }
        return ChunkVolume(sections, IntArray(ChunkVolume.COLUMNS) { seed % 5 })
    }

    private fun assertSame(expected: ChunkVolume, actual: ChunkVolume?) {
        actual!!
        for (section in 0 until ChunkVolume.SECTIONS) assertContentEquals(
            expected.section(section),
            actual.section(section),
            "section $section",
        )
        assertContentEquals(expected.biomes(), actual.biomes())
    }

    @Test
    fun everyEpochReadsBackItsOwnVersionWhileUnchangedSlotsWriteNothing() {
        val directory = Files.createTempDirectory("volume-store-test-")
        try {
            VolumeStore(directory, cacheEntries = 4).use { store ->
                val a = TileKey(0, 0)
                val b = TileKey(-3, 7)
                val oracle = mutableMapOf<Pair<TileKey, Long>, ChunkVolume>()
                var volumeA = terrain(1)
                var volumeB = terrain(2)
                val first = store.commit(10, mapOf(a to volumeA, b to volumeB))
                assertEquals(2, first.chunks)
                oracle[a to 10L] = volumeA
                oracle[b to 10L] = volumeB

                assertEquals(
                    0,
                    store.commit(11, mapOf(a to terrain(1))).chunks,
                    "an identical revisit is free",
                )

                volumeA = volumeA.edit { set(3, 20, 4, 0) }
                val tunnel = store.commit(12, mapOf(a to volumeA, b to volumeB))
                assertEquals(1, tunnel.chunks)
                assertEquals(1, tunnel.written, "one section of one chunk changed")
                oracle[a to 12L] = volumeA

                volumeB = volumeB.edit {
                    biome(0, 0, 21)
                    for (x in 0 until 16) for (z in 0 until 16) for (y in 80 until 96) set(
                        x,
                        y,
                        z,
                        4,
                    )
                }
                store.commit(13, mapOf(b to volumeB))
                oracle[b to 13L] = volumeB

                volumeA = volumeA.edit {
                    for (x in 0 until 16) for (z in 0 until 16) for (y in 0 until 16) set(
                        x,
                        y,
                        z,
                        0,
                    )
                }
                store.commit(14, mapOf(a to volumeA))
                oracle[a to 14L] = volumeA

                for (epoch in 10L..15L) for (key in listOf(a, b)) {
                    val expected =
                        oracle
                            .filterKeys { it.first == key && it.second <= epoch }
                            .maxBy { it.key.second }
                            .value
                    store.clearCache()
                    assertSame(expected, store.volume(key, epoch))
                }
                assertNull(store.volume(a, 9))
                assertNull(store.volume(a, 14)!!.section(0), "a dug-out section is absent again")
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun identicalSectionsAnywhereAreStoredOnce() {
        val directory = Files.createTempDirectory("volume-store-dedup-")
        try {
            VolumeStore(directory).use { store ->
                val flat =
                    ChunkVolume(
                        Array(ChunkVolume.SECTIONS) {
                            if (it < 4) IntArray(ChunkVolume.SECTION_BLOCKS) { 1 } else null
                        },
                        IntArray(ChunkVolume.COLUMNS),
                    )
                val commit = store.commit(1, (0 until 64).associate { TileKey(it, 0) to flat })
                assertEquals(2, commit.written, "one stone section and one biome grid")
                assertEquals(64 * 5 - 2, commit.reused)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
