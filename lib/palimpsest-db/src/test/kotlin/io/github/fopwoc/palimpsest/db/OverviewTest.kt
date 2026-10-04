package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.TestWorld.Companion.OVERWORLD
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.await
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.chunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverviewTest {
    /** Each chunk's terrain height tells which chunk a sample came from. */
    private val heights =
        mapOf(
            ChunkPos(1, 0) to 61,
            ChunkPos(1, 1) to 62,
            ChunkPos(0, 1) to 63,
            ChunkPos(40, 0) to 70,
            ChunkPos(0, 40) to 71,
        )

    private fun PalimpsestDb.fill() {
        val stone = vocabulary.id("minecraft:stone:0")
        val dimension = dimension(OVERWORLD)
        // (1, 1) first, then (1, 0): which one a cell shows depends on the moment.
        dimension
            .commit(
                WorldTick(1),
                listOf(chunk(ChunkPos(1, 1), stone, stone, height = { _, _ -> 62 })),
            )
            .await()
        dimension
            .commit(
                WorldTick(2),
                listOf(chunk(ChunkPos(1, 0), stone, stone, height = { _, _ -> 61 })),
            )
            .await()
        dimension
            .commit(
                WorldTick(3),
                listOf(ChunkPos(0, 40), ChunkPos(40, 0)).map { pos ->
                    chunk(pos, stone, stone, height = { _, _ -> heights.getValue(pos) })
                },
            )
            .await()
    }

    private fun PalimpsestDb.check() {
        val dimension = dimension(OVERWORLD)
        val near = ChunkWindow(0, 0, 2, 2)

        val chunks = dimension.at(WorldTick(3)).overview(near, 0).result.await()
        assertEquals(61, chunks.height(1, 0))
        assertFalse(chunks.present(0, 0))

        assertEquals(62, dimension.at(WorldTick(1)).overview(near, 1).result.await().height(0, 0))
        assertEquals(61, dimension.at(WorldTick(2)).overview(near, 1).result.await().height(0, 0))

        val wide =
            dimension.at(WorldTick(3)).overview(ChunkWindow(0, 0, 128, 128), 7).result.await()
        assertEquals(1, wide.width)
        assertTrue(wide.present(0, 0))
        // Region (0, 0) has chunks: the first region in Z-order wins.
        assertEquals(61, wide.height(0, 0))
    }

    @Test
    fun `far zoom picks the first existing chunk of each cell`() {
        TestWorld().use { world ->
            world.open().use {
                it.fill()
                it.check()
            }
            world.open().use { it.check() }
            world.config.cacheDirectory.toFile().deleteRecursively()
            world.open().use { it.check() }
        }
    }

    @Test
    fun `cells spanning regions follow region Z-order`() {
        TestWorld().use { world ->
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                val dimension = db.dimension(OVERWORLD)
                dimension
                    .commit(
                        WorldTick(1),
                        listOf(ChunkPos(0, 40), ChunkPos(40, 0)).map { pos ->
                            chunk(pos, stone, stone, height = { _, _ -> heights.getValue(pos) })
                        },
                    )
                    .await()
                val grid =
                    dimension.at(WorldTick(1)).overview(ChunkWindow(0, 0, 64, 64), 6).result.await()
                assertEquals(70, grid.height(0, 0))
            }
        }
    }
}
