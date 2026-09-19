package io.github.fopwoc.mods.palimpsest.tree

import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapTreeTest {
    private inline fun withDirectory(test: (Path) -> Unit) {
        val directory = Files.createTempDirectory("palimpsest-tree-")
        try {
            test(directory)
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }

    private fun tile(epoch: Long, seed: Int): TileRecord =
        TileRecord.build(
            epoch,
            { (seed + it % 3) and 0xFFFF },
            { 60 + seed % 5 + it / 64 },
            { 0 },
            { seed % 7 },
        )

    @Test
    fun commitsReadBackAtAnyEpochAndSurviveReopen() = withDirectory { directory ->
        val a = TileKey(3, -7)
        val b = TileKey(-1000, 512)
        MapTree(directory, machineId = 1).use { tree ->
            assertNull(tree.tile(a, Long.MAX_VALUE))
            tree.commit(100, mapOf(a to tile(100, 1), b to tile(100, 2)))
            tree.commit(200, mapOf(a to tile(200, 3)))
            assertEquals(tile(100, 1), tree.tile(a, 150))
            assertEquals(tile(200, 3), tree.tile(a, Long.MAX_VALUE))
            assertEquals(tile(100, 2), tree.tile(b, 200))
            assertNull(tree.tile(a, 99))
        }
        MapTree(directory, machineId = 1).use { tree ->
            assertEquals(200, tree.latestEpoch)
            assertEquals(tile(100, 1), tree.tile(a, 100))
            assertEquals(tile(200, 3), tree.tile(a, 200))
            assertEquals(listOf(200L, 100L), tree.history(a).map(TileRecord::epoch).toList())
        }
    }

    @Test
    fun unchangedSubtreesAreSharedBetweenRoots() = withDirectory { directory ->
        MapTree(directory, machineId = 1).use { tree ->
            val near = TileKey(0, 0)
            val far = TileKey(100_000, 100_000)
            tree.commit(1, mapOf(near to tile(1, 1), far to tile(1, 2)))
            val first = tree.roots.latest
            val farBefore = tree.tileRef(far, first)
            val result = tree.commit(2, mapOf(near to tile(2, 3)))
            val second = tree.roots.latest
            assertEquals(farBefore, tree.tileRef(far, second))
            assertNotEquals(tree.tileRef(near, first), tree.tileRef(near, second))
            // One tile plus the path above it; the far branch is untouched.
            assertEquals(1, result.tilesWritten)
            assertEquals(MapTree.LEVELS, result.nodesWritten)
            // A commit that changes nothing writes no tiles and no nodes, only a root.
            val noop = tree.commit(3, mapOf(near to tile(3, 3)))
            assertEquals(0, noop.tilesWritten)
            assertEquals(0, noop.nodesWritten)
            assertEquals(second, tree.roots.latest)
        }
    }

    @Test
    fun samplesComeFromParentsAndMatchTileCenters() = withDirectory { directory ->
        MapTree(directory, machineId = 1).use { tree ->
            val keys = (0 until 4).flatMap { z -> (0 until 4).map { x -> TileKey(x - 2, z - 2) } }
            tree.commit(10, keys.associateWith { tile(10, it.x * 16 + it.z + 100) })
            val level0 =
                tree.samples(
                    0,
                    MapTree.squareX(TileKey(-2, -2), 0),
                    MapTree.squareZ(TileKey(-2, -2), 0),
                    4,
                    10,
                )
            for ((index, key) in keys.withIndex()) {
                assertEquals(checkNotNull(tree.tile(key, 10)).sample, Sample(level0[index]))
            }
            val decodedBefore = tree.tilesDecoded()
            tree.samples(
                0,
                MapTree.squareX(TileKey(-2, -2), 0),
                MapTree.squareZ(TileKey(-2, -2), 0),
                4,
                10,
            )
            assertEquals(decodedBefore, tree.tilesDecoded())
            // Level 1: each square's sample is the first present quarter's tile centre.
            val level1 =
                tree.samples(
                    1,
                    MapTree.squareX(TileKey(-2, -2), 1),
                    MapTree.squareZ(TileKey(-2, -2), 1),
                    2,
                    10,
                )
            assertEquals(checkNotNull(tree.tile(TileKey(-2, -2), 10)).sample, Sample(level1[0]))
            assertEquals(checkNotNull(tree.tile(TileKey(0, 0), 10)).sample, Sample(level1[3]))
            val empty =
                tree.samples(
                    0,
                    MapTree.squareX(TileKey(500, 500), 0),
                    MapTree.squareZ(TileKey(500, 500), 0),
                    2,
                    10,
                )
            assertTrue(empty.all { Sample(it).isNone })
        }
    }

    @Test
    fun changedMatchesBruteForceAtEveryLevel() = withDirectory { directory ->
        MapTree(directory, machineId = 1).use { tree ->
            val random = Random(11)
            val area = (0 until 16).flatMap { z -> (0 until 16).map { x -> TileKey(x - 5, z - 9) } }
            var epoch = 1L
            val model = HashMap<TileKey, TileRecord>()
            val snapshots = ArrayList<Pair<Long, Map<TileKey, TileRecord>>>()
            repeat(12) {
                val changes =
                    area
                        .filter { random.nextInt(6) == 0 }
                        .associateWith { tile(epoch, random.nextInt(50)) }
                tree.commit(epoch, changes)
                model.putAll(changes)
                snapshots += epoch to HashMap(model)
                epoch += 10
            }
            for ((indexA, snapshotA) in snapshots.withIndex()) for ((indexB, snapshotB) in
                snapshots.withIndex()) {
                val (epochA, mapA) = snapshotA
                val (epochB, mapB) = snapshotB
                for (level in 0..4) {
                    val x0 = MapTree.squareX(TileKey(-5, -9), level)
                    val z0 = MapTree.squareZ(TileKey(-5, -9), level)
                    val side = (MapTree.squareX(TileKey(10, 6), level) - x0 + 1)
                    val expected = BooleanArray(side * side)
                    for (key in area) {
                        val a = mapA[key]
                        val b = mapB[key]
                        val differs = if (a == null || b == null) a !== b else !a.sameFacts(b)
                        if (differs) {
                            expected[
                                (MapTree.squareZ(key, level) - z0) * side +
                                    (MapTree.squareX(key, level) - x0)] = true
                        }
                    }
                    val actual = tree.changed(epochA, epochB, level, x0, z0, side)
                    // Structural diff is exact between neighbouring commits; across several it may
                    // also flag a tile that was rewritten and later restored to the same facts.
                    if (kotlin.math.abs(indexA - indexB) <= 1) {
                        assertContentEquals(
                            expected,
                            actual,
                            "epochs $epochA..$epochB level $level",
                        )
                    } else {
                        for (index in expected.indices) {
                            assertTrue(
                                !expected[index] || actual[index],
                                "epochs $epochA..$epochB level $level at $index",
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun randomCommitsAgreeWithAModel() = withDirectory { directory ->
        val random = Random(5)
        val keys =
            (0 until 40)
                .map { TileKey(random.nextInt(-300, 300), random.nextInt(-300, 300)) }
                .distinct()
        val model = HashMap<Pair<TileKey, Long>, TileRecord>()
        val epochs = ArrayList<Long>()
        var epoch = 1_700_000_000_000L
        MapTree(directory, machineId = 2, sealBytes = 8 shl 10).use { tree ->
            repeat(60) { step ->
                val changes = HashMap<TileKey, TileRecord>()
                for (key in keys) {
                    if (random.nextInt(4) != 0) continue
                    val current = tree.tile(key, Long.MAX_VALUE)
                    changes[key] =
                        if (current != null && random.nextBoolean()) {
                            val positions =
                                IntArray(1 + random.nextInt(5)) { random.nextInt(256) }
                                    .distinct()
                                    .toIntArray()
                            current.with(
                                epoch,
                                positions,
                                arrayOf(
                                    IntArray(positions.size) { random.nextInt(9) },
                                    IntArray(positions.size) { random.nextInt(256) },
                                    IntArray(positions.size) { random.nextInt(3) },
                                    IntArray(positions.size) { random.nextInt(4) },
                                ),
                            )
                        } else tile(epoch, random.nextInt(30))
                }
                tree.commit(epoch, changes)
                epochs += epoch
                for (key in keys) tree.tile(key, epoch)?.let { model[key to epoch] = it }
                if (step % 7 == 6) tree.sealIfDue()
                epoch += 60_000
            }
        }
        MapTree(directory, machineId = 2).use { tree ->
            assertEquals(epochs.last(), tree.latestEpoch)
            for ((keyAndEpoch, expected) in model) {
                val (key, at) = keyAndEpoch
                assertEquals(expected, tree.tile(key, at), "$key at $at")
                assertEquals(expected, tree.tile(key, at + 30_000), "$key just after $at")
            }
            for (key in keys) {
                val versions = tree.history(key).toList()
                assertEquals(
                    versions.map(TileRecord::epoch),
                    versions.map(TileRecord::epoch).sortedDescending(),
                )
            }
        }
    }
}
