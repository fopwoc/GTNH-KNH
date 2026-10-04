package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.TestWorld.Companion.await
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.chunk
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.loaded
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemapTest {
    private val dimension = DimensionId("remap")
    private val window = ChunkWindow(0, 0, 4, 4)

    /** Everything readable about the dimension, as plain values to compare. */
    private fun PalimpsestDb.everything(retention: Retention): List<Any> {
        val target = also { it.setRetention(dimension, retention) }.dimension(dimension).loaded()
        val ticks = target.timeline().map { it.tick }
        val result = ArrayList<Any>(listOf(ticks.map { it.value }))
        for (tick in ticks) {
            val snapshot = target.at(tick)
            val surface = snapshot.surface(window).result.await()
            result.add(
                (0 until 64 * 64).map {
                    surface.block(it % 64, it / 64).raw to surface.height(it % 64, it / 64)
                }
            )
            val overview = snapshot.overview(window, 1).result.await()
            result.add(
                (0 until 4).map {
                    overview.present(it % 2, it / 2) to overview.height(it % 2, it / 2)
                }
            )
            result.add(
                (0 until 16).map {
                    snapshot.volume(ChunkPos(it % 4, it / 4)).result.await()?.block(3, 70, 3)?.raw
                        ?: -1
                }
            )
        }
        result.add(
            target
                .diff(WorldTick(0), ticks.last())
                .result
                .await()
                .changes
                .map { it.pos to it.sections }
                .sortedBy { it.first.x * 100 + it.first.z }
        )
        return result
    }

    private fun check(retention: Retention, sessions: Int) {
        val messages = CopyOnWriteArrayList<String>()
        TestWorld().use { world ->
            val logging =
                DbConfig(
                    world.config.cacheDirectory,
                    world.config.blockKinds,
                    { _, _, message, _ -> messages += message },
                    2,
                    2,
                )
            for (session in 0 until sessions) {
                world.open().use { db ->
                    val stone = db.vocabulary.id("minecraft:stone:0")
                    val tower = db.vocabulary.id("tower:$session")
                    val positions =
                        (0 until 16)
                            .map { ChunkPos(it % 4, it / 4) }
                            .filter { (it.x + it.z + session) % 3 != 0 }
                    db.also { it.setRetention(dimension, retention) }
                        .dimension(dimension)
                        .commit(
                            WorldTick(session + 1L),
                            positions.map { pos ->
                                chunk(
                                    pos,
                                    stone,
                                    stone,
                                    height = { x, z -> 60 + (x + z + session) % 5 },
                                    extra = Triple(3, 70, 3) to tower,
                                )
                            },
                        )
                        .await()
                }
            }
            val remapped =
                (PalimpsestDb.open(world.world, logging) as OpenResult.Opened).db.use {
                    it.everything(retention)
                }
            assertTrue(messages.any { "index moved along" in it }, messages.joinToString("\n"))
            world.config.cacheDirectory.toFile().deleteRecursively()
            val rebuilt = world.open().use { it.everything(retention) }
            assertEquals(rebuilt, remapped)
        }
    }

    @Test fun `a full history's index follows its compaction`() = check(Retention.HISTORY, 16)

    @Test fun `a latest-only index follows its compaction`() = check(Retention.LATEST, 3)
}
