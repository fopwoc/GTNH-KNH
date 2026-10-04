package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.TestWorld.Companion.OVERWORLD
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.await
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.chunk
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.loaded
import java.util.concurrent.ExecutionException
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LifecycleTest {
    private val nether = DimensionId("nether")
    private val origin = ChunkPos(0, 0)

    private fun PalimpsestDb.commitTop(dimension: DimensionId, tick: Long, top: String) {
        val stone = vocabulary.id("minecraft:stone:0")
        dimension(dimension)
            .commit(WorldTick(tick), listOf(chunk(origin, stone, vocabulary.id(top))))
            .await()
    }

    private fun PalimpsestDb.top(dimension: DimensionId, tick: Long): String? =
        dimension(dimension)
            .at(WorldTick(tick))
            .volume(origin)
            .result
            .await()
            ?.block(0, 64, 0)
            ?.let(vocabulary::identity)

    @Test
    fun `retention is remembered and keeping less applies at the next compaction`() {
        TestWorld().use { world ->
            world.open().use { db ->
                db.setRetention(nether, Retention.LATEST)
                db.commitTop(nether, 1, "first:top")
            }
            world.open().use { db ->
                assertEquals(Retention.LATEST, db.dimension(nether).retention)
                db.commitTop(nether, 2, "second:top")
            }
            // Two sessions of a latest-only dimension: this open compacts history away.
            world.open().use { db ->
                assertEquals("second:top", db.top(nether, 2))
                assertEquals(null, db.top(nether, 1))
                db.setRetention(nether, Retention.HISTORY)
                db.commitTop(nether, 3, "third:top")
            }
            world.open().use { db ->
                assertEquals(Retention.HISTORY, db.dimension(nether).retention)
                assertEquals("second:top", db.top(nether, 2))
                assertEquals("third:top", db.top(nether, 3))
            }
        }
    }

    @Test
    fun `a dropped dimension is gone and starts again empty`() {
        TestWorld().use { world ->
            world.open().use { db ->
                db.commitTop(nether, 1, "doomed:top")
                db.commitTop(OVERWORLD, 1, "kept:top")
            }
            world.open().use { db ->
                val old = db.dimension(nether).loaded()
                db.drop(nether).await()
                val refused = runCatching {
                    old.commit(WorldTick(5), emptyList()).await()
                }
                    .exceptionOrNull()
                assertIs<IllegalStateException>(assertIs<ExecutionException>(refused).cause)
                assertTrue(db.dimension(nether).loaded().timeline().isEmpty())
                db.commitTop(nether, 7, "fresh:top")
            }
            world.open().use { db ->
                assertEquals(
                    listOf(7L),
                    db.dimension(nether).loaded().timeline().map { it.tick.value },
                )
                assertEquals("kept:top", db.top(OVERWORLD, 1))
            }
            assertEquals(
                1,
                world.world.resolve("dimensions/nether/segments").listDirectoryEntries().size,
            )
        }
    }

    @Test
    fun `closing in the background releases the world`() {
        TestWorld().use { world ->
            val db = world.open()
            db.commitTop(OVERWORLD, 1, "a:top")
            db.closeAsync().await()
            world.open().use { assertEquals("a:top", it.top(OVERWORLD, 1)) }
        }
    }
}
