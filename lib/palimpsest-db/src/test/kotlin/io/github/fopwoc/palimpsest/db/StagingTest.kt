package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.TestWorld.Companion.OVERWORLD
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.await
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.chunk
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.loaded
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
    fun `commits queued back to back are written in tick order`() {
        TestWorld().use { world ->
            val rounds = 200
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                val dimension = db.dimension(OVERWORLD)
                val commits =
                    (1..rounds).map { round ->
                        dimension.stage(
                            chunk(ChunkPos(0, 0), stone, db.vocabulary.id("top:${round % 2}"))
                        )
                        dimension.commitStaged(WorldTick(1))
                    }
                commits.forEach { it.await() }
                assertEquals(
                    (1L..rounds).toList(),
                    dimension.timeline().map { it.tick.value },
                )
            }
            world.open().use { db ->
                assertEquals(rounds, db.dimension(OVERWORLD).loaded().timeline().size)
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
