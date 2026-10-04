package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.TestWorld.Companion.VOLUME
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.await
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.chunk
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SurfaceModeTest {
    private val full = DimensionId("full")
    private val flat = DimensionId("flat")
    private val surfaceOnly = DimensionMode(Depth.SURFACE, Retention.HISTORY)
    private val window = ChunkWindow(0, 0, 3, 3)

    private fun PalimpsestDb.both(
        tick: Long,
        observations: List<ChunkObservation>,
    ): Pair<Commit, Commit> =
        dimension(full, VOLUME).commit(WorldTick(tick), observations).await() to
            dimension(flat, surfaceOnly).commit(WorldTick(tick), observations).await()

    private fun PalimpsestDb.assertSame(tick: Long) {
        val a = dimension(full, VOLUME).at(WorldTick(tick)).surface(window).result.await()
        val b = dimension(flat, surfaceOnly).at(WorldTick(tick)).surface(window).result.await()
        for (z in 0 until 48) for (x in 0 until 48) {
            assertEquals(a.block(x, z), b.block(x, z))
            assertEquals(a.height(x, z), b.height(x, z))
            assertEquals(a.biome(x, z), b.biome(x, z))
        }
    }

    @Test
    fun `a surface-only dimension keeps the map and nothing below it`() {
        TestWorld().use { world ->
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                val grass = db.vocabulary.id("minecraft:grass:0")
                val torch = db.vocabulary.id("minecraft:torch:5")
                val positions = window(0, 0)
                db.both(
                    1,
                    positions.map {
                        chunk(it, stone, grass, height = { x, z -> 60 + (x + z) % 9 })
                    },
                )
                val (_, dug) =
                    db.both(
                        2,
                        listOf(
                            chunk(
                                ChunkPos(1, 1),
                                stone,
                                grass,
                                height = { x, z -> 60 + (x + z) % 9 },
                                extra = Triple(4, 8, 4) to BlockId.AIR,
                            )
                        ),
                    )
                assertEquals(0, dug.chunksChanged)
                val (_, built) =
                    db.both(
                        3,
                        listOf(
                            chunk(
                                ChunkPos(1, 1),
                                stone,
                                grass,
                                height = { x, z -> 60 + (x + z) % 9 },
                                extra = Triple(4, 90, 4) to torch,
                            )
                        ),
                    )
                assertEquals(1, built.chunksChanged)

                db.assertSame(1)
                db.assertSame(3)
                val flatDimension = db.dimension(flat, surfaceOnly)
                assertNull(flatDimension.at(WorldTick(3)).volume(ChunkPos(1, 1)).result.await())
                assertEquals(
                    torch,
                    flatDimension.at(WorldTick(3)).ceiling(window, 40).result.await().block(20, 20),
                )
                assertTrue(
                    flatDimension
                        .diff(WorldTick(1), WorldTick(3))
                        .result
                        .await()
                        .changes
                        .single()
                        .surface
                )
                assertTrue(
                    flatDimension
                        .at(WorldTick(3))
                        .overview(ChunkWindow(1, 1, 1, 1), 0)
                        .result
                        .await()
                        .present(1, 1)
                )
            }
            fun size(dimension: DimensionId) =
                Files.walk(world.world.resolve("dimensions/${dimension.key}")).use { files ->
                    files.filter(Files::isRegularFile).mapToLong(Files::size).sum()
                }
            assertTrue(
                size(flat) * 5 < size(full),
                "surface ${size(flat)} B vs full ${size(full)} B",
            )

            world.config.cacheDirectory.toFile().deleteRecursively()
            world.open().use { db ->
                db.assertSame(1)
                db.assertSame(3)
            }
        }
    }

    private fun window(x: Int, z: Int) =
        (0 until 3).flatMap { dz -> (0 until 3).map { dx -> ChunkPos(x + dx, z + dz) } }
}
