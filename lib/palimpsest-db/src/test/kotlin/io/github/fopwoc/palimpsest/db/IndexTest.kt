package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.TestWorld.Companion.OVERWORLD
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.VOLUME
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.await
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.chunk
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals

class IndexTest {
    /** Chunks far apart, so every one lands in its own index region. */
    private val scattered = List(80) { ChunkPos(it * 40, -it * 33) }

    private fun PalimpsestDb.fill() {
        val stone = vocabulary.id("minecraft:stone:0")
        val dimension = dimension(OVERWORLD, VOLUME)
        scattered.forEachIndexed { i, pos ->
            val top = vocabulary.id("test:top:$i")
            dimension.commit(WorldTick(10 + i.toLong()), listOf(chunk(pos, stone, top))).await()
        }
    }

    private fun PalimpsestDb.assertFilled() {
        val dimension = dimension(OVERWORLD, VOLUME)
        assertEquals(scattered.size, dimension.timeline().count { it.tick.value >= 10 })
        val latest = dimension.at(WorldTick(Long.MAX_VALUE))
        scattered.forEachIndexed { i, pos ->
            assertEquals(
                "test:top:$i",
                vocabulary.identity(latest.volume(pos).result.await()!!.block(1, 64, 1)),
            )
        }
        // Each chunk existed only from its own commit on.
        assertEquals(null, dimension.at(WorldTick(50)).volume(scattered[60]).result.await())
    }

    @Test
    fun `regions dropped from memory read back from disk`() {
        TestWorld().use { world ->
            world.open().use { db ->
                db.fill()
                db.flush()
                db.assertFilled()
            }
            world.open().use { it.assertFilled() }
        }
    }

    @Test
    fun `a lost index is rebuilt from history`() {
        TestWorld().use { world ->
            world.open().use { it.fill() }
            world.config.cacheDirectory.toFile().deleteRecursively()
            world.open().use { it.assertFilled() }
            world.open().use { it.assertFilled() }
        }
    }

    @Test
    fun `an index behind history catches up`() {
        TestWorld().use { world ->
            val stale = world.root.resolve("stale-cache")
            world.open().use { db ->
                val stone = db.vocabulary.id("minecraft:stone:0")
                db.dimension(OVERWORLD, VOLUME)
                    .commit(WorldTick(1), listOf(chunk(scattered[0], stone, stone)))
                    .await()
                db.flush()
                world.config.cacheDirectory.toFile().copyRecursively(stale.toFile())
            }
            world.open().use { it.fill() }
            // The index as a crash would leave it: flushed long before history moved on.
            world.config.cacheDirectory.toFile().deleteRecursively()
            stale.toFile().copyRecursively(world.config.cacheDirectory.toFile())
            world.open().use { it.assertFilled() }
        }
    }

    @Test
    fun `an index file cut short is not trusted`() {
        TestWorld().use { world ->
            world.open().use { it.fill() }
            // A system crash can lose unsynced index writes that the coverage already counts.
            val region =
                world.config.cacheDirectory
                    .resolve("overworld/regions")
                    .listDirectoryEntries()
                    .first()
            java.nio.channels.FileChannel.open(region, java.nio.file.StandardOpenOption.WRITE).use {
                it.truncate(it.size() / 2)
            }
            world.open().use { it.assertFilled() }
        }
    }
}
