package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.TestWorld.Companion.OVERWORLD
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.await
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.chunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceTest {
    private val window = ChunkWindow(0, 0, 2, 1)

    @Test
    fun `the map sees every moment from above`() {
        TestWorld().use { world ->
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                val grass = db.vocabulary.id("minecraft:grass:0")
                val ore = db.vocabulary.id("minecraft:iron_ore:0")
                val dimension = db.dimension(OVERWORLD)
                dimension.commit(WorldTick(1), listOf(chunk(ChunkPos(0, 0), stone, grass))).await()
                // Digging out of sight changes history but not the surface.
                dimension
                    .commit(
                        WorldTick(2),
                        listOf(
                            chunk(ChunkPos(0, 0), stone, grass, extra = Triple(4, 10, 4) to ore)
                        ),
                    )
                    .await()
                dimension
                    .commit(
                        WorldTick(3),
                        listOf(
                            chunk(
                                ChunkPos(0, 0),
                                stone,
                                grass,
                                height = { x, _ -> if (x < 8) 70 else 64 },
                            )
                        ),
                    )
                    .await()

                val first = dimension.at(WorldTick(2)).surface(window).result.await()
                assertTrue(first.present(ChunkPos(0, 0)))
                assertFalse(first.present(ChunkPos(1, 0)))
                assertEquals(grass, first.block(3, 3))
                assertEquals(64, first.height(3, 3))
                assertEquals(BiomeId(1), first.biome(3, 3))

                val later = dimension.at(WorldTick(3)).surface(window).result.await()
                assertEquals(70, later.height(3, 3))
                assertEquals(64, later.height(12, 3))
            }
        }
    }

    @Test
    fun `new block kinds rebuild the surfaces`() {
        TestWorld().use { world ->
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                val glass = db.vocabulary.id("minecraft:glass:0")
                db.dimension(OVERWORLD)
                    .commit(WorldTick(1), listOf(chunk(ChunkPos(0, 0), stone, glass)))
                    .await()
            }
            val seeThrough =
                DbConfig(
                    world.config.cacheDirectory,
                    { if (it.contains("glass")) BlockKind.TRANSPARENT else BlockKind.SOLID },
                    world.config.log,
                    2,
                    2,
                )
            val db = (PalimpsestDb.open(world.world, seeThrough) as OpenResult.Opened).db
            db.use {
                val grid = db.dimension(OVERWORLD).at(WorldTick(1)).surface(window).result.await()
                assertEquals("minecraft:stone:0", db.vocabulary.identity(grid.block(3, 3)))
                assertEquals(63, grid.height(3, 3))
            }
        }
    }

    @Test
    fun `a ceiling view reads history from below a roof`() {
        TestWorld().use { world ->
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                val grass = db.vocabulary.id("minecraft:grass:0")
                val dimension = db.dimension(OVERWORLD)
                // A one-block hole at y 40 with stone below: from a ceiling at 40 the floor is at
                // 39.
                dimension
                    .commit(
                        WorldTick(1),
                        listOf(
                            chunk(
                                ChunkPos(0, 0),
                                stone,
                                grass,
                                extra = Triple(3, 40, 3) to BlockId.AIR,
                            )
                        ),
                    )
                    .await()
                val cave = dimension.at(WorldTick(1)).ceiling(window, 40).result.await()
                assertEquals(39, cave.height(3, 3))
                assertEquals(40, cave.height(4, 4))
                val sky = dimension.at(WorldTick(1)).ceiling(window, 255).result.await()
                assertEquals(64, sky.height(3, 3))
                assertFalse(cave.present(ChunkPos(1, 0)))
            }
        }
    }
}
