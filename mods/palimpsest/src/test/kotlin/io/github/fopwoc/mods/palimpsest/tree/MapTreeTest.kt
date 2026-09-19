package io.github.fopwoc.mods.palimpsest.tree

import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
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
            assertEquals(tree.roots.latest.level, result.nodesWritten)
            // A commit that changes nothing writes no tiles and no nodes, only a root.
            val noop = tree.commit(3, mapOf(near to tile(3, 3)))
            assertEquals(0, noop.tilesWritten)
            assertEquals(0, noop.nodesWritten)
            assertEquals(second.ref, tree.roots.latest.ref)
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
                val actual = tree.tile(key, at)
                assertEquals(expected, actual, "$key at $at: epochs ${expected.epoch} vs ${actual?.epoch}, sameFacts=${actual?.let { expected.sameFacts(it) }}")
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

    @Test
    fun rootSquareGrowsWithTheWorldAndOldRootsStayReadable() = withDirectory { directory ->
        MapTree(directory, machineId = 1).use { tree ->
            val near = TileKey(0, 0)
            tree.commit(1, mapOf(near to tile(1, 1), TileKey(1, 1) to tile(1, 2)))
            val small = tree.roots.latest
            assertTrue(small.level < MapTree.LEVELS, "root level ${small.level}")
            val far = TileKey(-5000, 7000)
            val grown = tree.commit(2, mapOf(far to tile(2, 3)))
            val big = tree.roots.latest
            assertTrue(big.level > small.level)
            // Growing wrote the lifting chain plus the far tile's path, not a whole-world path.
            assertTrue(grown.nodesWritten < MapTree.LEVELS + big.level, "nodes ${grown.nodesWritten}")
            assertEquals(tile(1, 1), tree.tile(near, 2))
            assertEquals(tile(2, 3), tree.tile(far, 2))
            assertEquals(tile(1, 1), tree.tile(near, 1))
            assertNull(tree.tile(far, 1))
            // Samples above the small root: the one square holding it.
            val above = tree.samples(small.level + 2, MapTree.squareX(near, small.level + 2) - 1, MapTree.squareZ(near, small.level + 2) - 1, 3, 1)
            assertEquals(checkNotNull(tree.tile(near, 1)).sample, Sample(above[4]))
            // Changes between the two commits, at the level just below the old root.
            val level = small.level - 1
            val x0 = minOf(MapTree.squareX(near, level), MapTree.squareX(far, level))
            val z0 = minOf(MapTree.squareZ(near, level), MapTree.squareZ(far, level))
            val side = maxOf(MapTree.squareX(near, level), MapTree.squareX(far, level)) - x0 + 1
            val sideZ = maxOf(MapTree.squareZ(near, level), MapTree.squareZ(far, level)) - z0 + 1
            val changed = tree.changed(1, 2, level, x0, z0, maxOf(side, sideZ))
            assertTrue(changed[(MapTree.squareZ(far, level) - z0) * maxOf(side, sideZ) + (MapTree.squareX(far, level) - x0)])
            assertFalse(changed[(MapTree.squareZ(near, level) - z0) * maxOf(side, sideZ) + (MapTree.squareX(near, level) - x0)])
        }
        MapTree(directory, machineId = 1).use { tree ->
            assertEquals(tile(2, 3), tree.tile(TileKey(-5000, 7000), Long.MAX_VALUE))
            assertEquals(tile(1, 1), tree.tile(TileKey(0, 0), 1))
        }
    }

    @Test
    fun repeatedCommitsToOneTileWritePatchNodesAndStayReadable() = withDirectory { directory ->
        MapTree(directory, machineId = 1).use { tree ->
            val keys = (0 until 8).map { TileKey(it % 4, it / 4) }
            tree.commit(1, keys.associateWith { tile(1, it.x + it.z * 4) })
            fun edited(step: Int): TileRecord =
                tile(1, 5).with(step.toLong(), intArrayOf(7), arrayOf(intArrayOf(step), intArrayOf(64), intArrayOf(0), intArrayOf(0)))
            val first = tree.commit(2, mapOf(TileKey(1, 1) to edited(2)))
            var last = first
            for (step in 3..40) last = tree.commit(step.toLong(), mapOf(TileKey(1, 1) to edited(step)))
            // A one-pixel commit: one small delta, the path above it as patch nodes, one root.
            assertEquals(tree.roots.latest.level, last.nodesWritten)
            assertTrue(last.bytes < 90, "one-pixel commit: ${last.bytes} bytes")
            assertTrue(first.bytes < 90, "first one-pixel commit: ${first.bytes} bytes")
            for (step in 1..40) {
                val expected = if (step == 1) tile(1, 5) else edited(step)
                assertEquals(expected, tree.tile(TileKey(1, 1), step.toLong()), "at $step")
                assertEquals(tile(1, 0), tree.tile(TileKey(0, 0), step.toLong()))
            }
        }
    }

    @Test
    fun identicalTilesAreStoredOnceAndLinksSurviveSealAndReopen() = withDirectory { directory ->
        val ocean = TileRecord.solid(0, block = 7, height = 62, depth = 20, biome = 0)
        val keys = (0 until 64).map { TileKey(it % 8, it / 8) }
        MapTree(directory, machineId = 1, sealBytes = 1 shl 12).use { tree ->
            val first = tree.commit(1, keys.associateWith { ocean.withEpoch(1) })
            assertEquals(64, first.tilesWritten)
            assertEquals(63, first.tilesLinked)
            assertEquals(1, tree.contentSize)
            // Links are a dozen bytes; the whole commit is far under one full record per tile.
            assertTrue(first.bytes < 64 * 20 + 64 * 45, "commit bytes ${first.bytes}")
            tree.seal()
            // A tile that changes and later returns to the ocean links back to the shared record.
            val island = ocean.with(2, intArrayOf(100, 101), arrayOf(intArrayOf(3, 3), intArrayOf(64, 64), intArrayOf(0, 0), intArrayOf(0, 0)))
            tree.commit(2, mapOf(keys[5] to island))
            val back = tree.commit(3, mapOf(keys[5] to ocean.withEpoch(3)))
            assertEquals(1, back.tilesLinked)
            assertEquals(ocean.withEpoch(3), tree.tile(keys[5], 3))
            assertEquals(island, tree.tile(keys[5], 2))
        }
        MapTree(directory, machineId = 1).use { tree ->
            assertEquals(1, tree.contentSize)
            for (key in keys) assertEquals(if (key == keys[5]) ocean.withEpoch(3) else ocean.withEpoch(1), tree.tile(key, Long.MAX_VALUE))
            assertEquals(1, tree.commit(4, mapOf(TileKey(20, 20) to ocean.withEpoch(4))).tilesLinked)
        }
    }
}
