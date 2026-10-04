package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.TestWorld.Companion.OVERWORLD
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.await
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.chunk
import kotlin.test.Test
import kotlin.test.assertEquals

class StagingTest {
    @Test
    fun `staging keeps the newest look per chunk until a commit`() {
        TestWorld().use { world ->
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                val dimension = db.dimension(OVERWORLD)
                val origin = ChunkPos(0, 0)
                for (top in 1..5) dimension.stage(
                    chunk(origin, stone, db.vocabulary.id("top:$top"))
                )
                dimension.stage(chunk(ChunkPos(1, 0), stone, stone))
                assertEquals(2, dimension.stagedCount)
                assertEquals(2, dimension.commitStaged(WorldTick(1)).await().chunksChanged)
                assertEquals(0, dimension.stagedCount)
                assertEquals(
                    "top:5",
                    db.vocabulary.identity(
                        dimension.at(WorldTick(1)).volume(origin).result.await()!!.block(0, 64, 0)
                    ),
                )
                // Nothing staged: no moment is spent.
                assertEquals(0, dimension.commitStaged(WorldTick(2)).await().chunksChanged)
                assertEquals(listOf(1L), dimension.timeline().map { it.tick.value })
            }
        }
    }

    @Test
    fun `a staged commit at a tick already used goes right after the last one`() {
        TestWorld().use { world ->
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                db.dimension(OVERWORLD)
                    .commit(WorldTick(5), listOf(chunk(ChunkPos(0, 0), stone, stone)))
                    .await()
            }
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                val dimension = db.dimension(OVERWORLD)
                // Staged before loading finishes: the last tick is not known yet.
                dimension.stage(chunk(ChunkPos(1, 0), stone, stone))
                assertEquals(6, dimension.commitStaged(WorldTick(3)).await().tick.value)
                dimension.stage(chunk(ChunkPos(2, 0), stone, stone))
                assertEquals(9, dimension.commitStaged(WorldTick(9)).await().tick.value)
                assertEquals(listOf(5L, 6L, 9L), dimension.timeline().map { it.tick.value })
            }
        }
    }
}
