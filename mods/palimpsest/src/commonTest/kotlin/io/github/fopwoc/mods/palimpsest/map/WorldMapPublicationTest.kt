package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WorldMapPublicationTest {
    @Test
    fun closedMapPublishesHistoryAndDictionaryForAnotherMachine() =
        TestBlocks.withDirectory("world-publication-") { directory ->
            val origin = directory.resolve("origin")
            val vocabulary = TestBlocks.table(origin)
            WorldMap(origin.resolve("y255"), vocabulary).use {
                it.observe(0, 0, TestBlocks.flat(1))
                it.observe(0, 0, TestBlocks.flat(1))
                it.flush()
                assertEquals(3, BlockTable(origin, vocabulary.machineId).size)
            }
            val shared = directory.resolve("shared")
            Files.walk(origin).use { paths ->
                for (path in paths.toList()) {
                    val target = shared.resolve(origin.relativize(path))
                    if (Files.isDirectory(path)) Files.createDirectories(target)
                    else if (
                        !path.fileName.toString().startsWith("active-") &&
                            !path.fileName.toString().endsWith(".lock")
                    )
                        Files.copy(path, target)
                }
            }
            val other = BlockTable(shared, 123)
            MapTree(shared.resolve("y255"), 123, translateBlock = other::translate).use {
                val tile = assertNotNull(it.tile(TileKey(0, 0), Long.MAX_VALUE))
                assertEquals(TestBlocks.RED, other.color(tile.block(0)))
            }
        }
}
