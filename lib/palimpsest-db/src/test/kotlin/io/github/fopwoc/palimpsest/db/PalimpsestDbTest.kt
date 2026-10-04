package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.TestWorld.Companion.OVERWORLD
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.VOLUME
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.await
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.chunk
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.loaded
import java.util.concurrent.ExecutionException
import kotlin.io.path.deleteExisting
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PalimpsestDbTest {
    private val origin = ChunkPos(0, 0)

    @Test
    fun `every moment reads back as it was`() =
        TestWorld().use { world ->
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                val grass = db.vocabulary.id("minecraft:grass:0")
                val torch = db.vocabulary.id("minecraft:torch:5")
                val dimension = db.dimension(OVERWORLD, VOLUME)
                val first =
                    dimension.commit(WorldTick(100), listOf(chunk(origin, stone, grass))).await()
                assertEquals(1, first.chunksChanged)
                val same =
                    dimension.commit(WorldTick(200), listOf(chunk(origin, stone, grass))).await()
                assertEquals(0, same.chunksChanged)
                val lit =
                    dimension
                        .commit(
                            WorldTick(300),
                            listOf(chunk(origin, stone, grass, extra = Triple(3, 65, 4) to torch)),
                        )
                        .await()
                assertEquals(1, lit.chunksChanged)
                assertEquals(1, lit.sectionsWritten)

                val before = dimension.at(WorldTick(299)).volume(origin).result.await()!!
                val after = dimension.at(WorldTick(300)).volume(origin).result.await()!!
                assertEquals(BlockId.AIR, before.block(3, 65, 4))
                assertEquals(torch, after.block(3, 65, 4))
                assertEquals(grass, after.block(0, 64, 0))
                assertEquals(stone, after.block(15, 0, 15))
                assertNull(dimension.at(WorldTick(99)).volume(origin).result.await())
                assertEquals(listOf(100L, 300L), dimension.timeline().map { it.tick.value })
            }
        }

    @Test
    fun `history survives reopening and keeps deduplicating`() =
        TestWorld().use { world ->
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                val grass = db.vocabulary.id("minecraft:grass:0")
                db.dimension(OVERWORLD, VOLUME)
                    .commit(WorldTick(10), listOf(chunk(origin, stone, grass)))
                    .await()
            }
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                val grass = db.vocabulary.id("minecraft:grass:0")
                assertEquals(1, stone.raw)
                val dimension = db.dimension(OVERWORLD, VOLUME).loaded()
                assertEquals(WorldTick(10), dimension.latest?.tick)
                assertEquals(
                    grass,
                    dimension.at(WorldTick(10)).volume(origin).result.await()!!.block(5, 64, 5),
                )
                val neighbour =
                    dimension
                        .commit(WorldTick(20), listOf(chunk(ChunkPos(1, 0), stone, grass)))
                        .await()
                assertEquals(1, neighbour.chunksChanged)
                assertEquals(0, neighbour.sectionsWritten)
            }
        }

    @Test
    fun `world time going backwards is refused`() {
        TestWorld().use { world ->
            world.open().use { db ->
                val dimension = db.dimension(OVERWORLD, VOLUME)
                val stone = db.vocabulary.id("minecraft:stone:0")
                dimension.commit(WorldTick(50), listOf(chunk(origin, stone, stone))).await()
                val refused = runCatching {
                    dimension.commit(WorldTick(50), emptyList()).await()
                }
                    .exceptionOrNull()
                assertIs<TickOrderException>(assertIs<ExecutionException>(refused).cause)
            }
        }
    }

    @Test
    fun `a world opens once`() =
        TestWorld().use { world ->
            world.open().use {
                assertIs<OpenResult.Locked>(PalimpsestDb.open(world.world, world.config))
            }
            world.open().close()
        }

    @Test
    fun `a crash loses only what was not flushed`() =
        TestWorld().use { world ->
            val copy = world.root.resolve("crashed")
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                val dimension = db.dimension(OVERWORLD, VOLUME)
                dimension.commit(WorldTick(1), listOf(chunk(origin, stone, stone))).await()
                db.flush()
                val dirt = db.vocabulary.id("minecraft:dirt:0")
                dimension.commit(WorldTick(2), listOf(chunk(origin, stone, dirt))).await()
                // The folder as a killed game would leave it: data written, manifest from the
                // flush.
                world.world.toFile().copyRecursively(copy.toFile())
            }
            world.open(copy).use { db ->
                val dimension = db.dimension(OVERWORLD, VOLUME).loaded()
                assertEquals(listOf(1L), dimension.timeline().map { it.tick.value })
                val stone = db.vocabulary.id("minecraft:stone:0")
                assertEquals(
                    stone,
                    dimension.at(WorldTick(5)).volume(origin).result.await()!!.block(0, 64, 0),
                )
                assertEquals(2, db.vocabulary.size)
                assertEquals(
                    1,
                    dimension
                        .commit(
                            WorldTick(3),
                            listOf(chunk(origin, stone, db.vocabulary.id("minecraft:sand:0"))),
                        )
                        .await()
                        .chunksChanged,
                )
            }
            world.open(copy).use { db ->
                assertEquals(
                    listOf(1L, 3L),
                    db.dimension(OVERWORLD, VOLUME).loaded().timeline().map { it.tick.value },
                )
            }
        }

    @Test
    fun `a half-synced folder does not open`() =
        TestWorld().use { world ->
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                db.dimension(OVERWORLD, VOLUME)
                    .commit(WorldTick(1), listOf(chunk(origin, stone, stone)))
                    .await()
            }
            world.world
                .resolve("dimensions/overworld/segments")
                .listDirectoryEntries()
                .single()
                .deleteExisting()
            val result = PalimpsestDb.open(world.world, world.config)
            assertTrue(
                assertIs<OpenResult.SyncIncomplete>(result)
                    .missing
                    .single()
                    .startsWith("dimensions")
            )
        }
}
