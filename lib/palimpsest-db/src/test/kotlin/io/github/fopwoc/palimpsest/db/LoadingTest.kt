package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.TestWorld.Companion.OVERWORLD
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.VOLUME
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.await
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.chunk
import java.util.concurrent.ExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoadingTest {
    private val origin = ChunkPos(0, 0)

    @Test
    fun `work asked for while a dimension loads runs after it`() {
        TestWorld().use { world ->
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                val dimension = db.dimension(OVERWORLD, VOLUME)
                for (tick in 1L..5) dimension
                    .commit(
                        WorldTick(tick),
                        (0 until 20).map {
                            chunk(ChunkPos(it, tick.toInt()), stone, db.vocabulary.id("top:$tick"))
                        },
                    )
                    .await()
            }
            world.config.cacheDirectory.toFile().deleteRecursively()
            world.open().use { db ->
                val dimension = db.dimension(OVERWORLD, VOLUME)
                // Asked for at once, while the index is rebuilt in the background.
                val past = dimension.at(WorldTick(3)).surface(ChunkWindow(0, 3, 20, 1))
                val commit =
                    dimension.commit(
                        WorldTick(6),
                        listOf(
                            chunk(
                                origin,
                                db.vocabulary.id("minecraft:stone:0"),
                                db.vocabulary.id("top:6"),
                            )
                        ),
                    )
                assertEquals(
                    "top:3",
                    db.vocabulary.identity(past.result.await().block(5, 3 * 16 + 5)),
                )
                assertEquals(1, commit.await().chunksChanged)
                assertTrue(dimension.ready.isDone)
                assertEquals(6, dimension.timeline().size)
                assertEquals(emptyList(), db.activity)
            }
        }
    }

    @Test
    fun `a commit older than history is dropped alone`() {
        TestWorld().use { world ->
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                db.dimension(OVERWORLD, VOLUME)
                    .commit(WorldTick(10), listOf(chunk(origin, stone, stone)))
                    .await()
            }
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                val dimension = db.dimension(OVERWORLD, VOLUME)
                val stale =
                    dimension.commit(
                        WorldTick(5),
                        listOf(chunk(origin, stone, db.vocabulary.id("stale:top"))),
                    )
                val fresh =
                    dimension.commit(
                        WorldTick(11),
                        listOf(chunk(origin, stone, db.vocabulary.id("fresh:top"))),
                    )
                assertIs<TickOrderException>(
                    assertIs<ExecutionException>(runCatching { stale.await() }.exceptionOrNull())
                        .cause
                )
                assertEquals(1, fresh.await().chunksChanged)
            }
        }
    }
}
