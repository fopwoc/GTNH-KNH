package io.github.fopwoc.mods.palimpsest.tree

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ActiveContentReplayTest {
    @Test
    fun activeFullHashesSurviveReopenAndLaterSealing() {
        val directory = Files.createTempDirectory("active-content-")
        try {
            val original = TileRecord.solid(1, 7, 64)
            MapTree(directory, 1).use { it.commit(1, mapOf(TileKey(0, 0) to original)) }
            MapTree(directory, 1).use {
                assertEquals(1, it.contentSize)
                assertEquals(
                    1,
                    it.commit(2, mapOf(TileKey(1, 0) to original.withEpoch(2))).tilesLinked,
                )
                it.seal()
            }
            MapTree(directory, 1).use {
                assertEquals(1, it.contentSize)
                assertEquals(
                    1,
                    it.commit(3, mapOf(TileKey(2, 0) to original.withEpoch(3))).tilesLinked,
                )
                assertEquals(original, it.tile(TileKey(0, 0), 1))
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
