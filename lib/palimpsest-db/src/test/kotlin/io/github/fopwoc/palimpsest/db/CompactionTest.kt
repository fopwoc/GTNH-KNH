package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.TestWorld.Companion.OVERWORLD
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.await
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.chunk
import java.nio.file.Files
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompactionTest {
    private val nether = DimensionId("nether")
    private val a = ChunkPos(0, 0)
    private val b = ChunkPos(5, 5)

    private fun TestWorld.session(
        dimension: DimensionId,
        retention: Retention,
        tick: Long,
        chunks: Map<ChunkPos, String>,
    ) =
        open().use { db ->
            val stone = db.vocabulary.id("minecraft:stone:0")
            db.also { it.setRetention(dimension, retention) }
                .dimension(dimension)
                .commit(
                    WorldTick(tick),
                    chunks.map { (pos, top) -> chunk(pos, stone, db.vocabulary.id(top)) },
                )
                .await()
        }

    private fun TestWorld.top(
        dimension: DimensionId,
        retention: Retention,
        tick: Long,
        pos: ChunkPos,
    ): String? =
        open().use { db ->
            db.also { it.setRetention(dimension, retention) }
                .dimension(dimension)
                .at(WorldTick(tick))
                .volume(pos)
                .result
                .await()
                ?.block(0, 64, 0)
                ?.let(db.vocabulary::identity)
        }

    private fun TestWorld.segments(dimension: DimensionId) =
        world.resolve("dimensions/${dimension.key}/segments").listDirectoryEntries()

    @Test
    fun `a latest-only dimension forgets history it outgrew`() {
        TestWorld().use { world ->
            world.session(nether, Retention.LATEST, 1, mapOf(a to "old:netherrack"))
            world.session(
                nether,
                Retention.LATEST,
                2,
                mapOf(a to "new:netherrack", b to "b:netherrack"),
            )
            val before = world.segments(nether).sumOf { it.fileSize() }
            assertEquals(2, world.segments(nether).size)

            assertEquals("new:netherrack", world.top(nether, Retention.LATEST, 2, a))
            assertEquals(1, world.segments(nether).size)
            assertTrue(world.segments(nether).single().fileSize() < before)
            assertNull(
                world.top(nether, Retention.LATEST, 1, a),
                "history before the last change is gone",
            )
            assertEquals("b:netherrack", world.top(nether, Retention.LATEST, 2, b))

            world.session(nether, Retention.LATEST, 3, mapOf(b to "later:netherrack"))
            assertEquals("later:netherrack", world.top(nether, Retention.LATEST, 3, b))
            assertEquals("new:netherrack", world.top(nether, Retention.LATEST, 3, a))
        }
    }

    @Test
    fun `many sessions of full history merge into one segment`() {
        TestWorld().use { world ->
            for (session in 1..16) world.session(
                OVERWORLD,
                Retention.HISTORY,
                session.toLong(),
                mapOf(a to "top:$session", ChunkPos(session * 3, 0) to "far:$session"),
            )
            assertEquals(16, world.segments(OVERWORLD).size)
            for (session in 1..16) assertEquals(
                "top:$session",
                world.top(OVERWORLD, Retention.HISTORY, session.toLong(), a),
            )
            assertEquals(1, world.segments(OVERWORLD).size)
            world.config.cacheDirectory.toFile().deleteRecursively()
            for (session in listOf(1, 7, 16)) {
                assertEquals(
                    "top:$session",
                    world.top(OVERWORLD, Retention.HISTORY, session.toLong(), a),
                )
                assertEquals(
                    "far:$session",
                    world.top(OVERWORLD, Retention.HISTORY, 16, ChunkPos(session * 3, 0)),
                )
            }
            assertTrue(Files.exists(world.world.resolve("manifests")))
        }
    }
}
