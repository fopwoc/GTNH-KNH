package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.TestWorld.Companion.OVERWORLD
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.await
import io.github.fopwoc.palimpsest.db.TestWorld.Companion.chunk
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeltaHistoryTest {
    private val origin = ChunkPos(0, 0)
    private val edits = 20

    /** Commit k places a torch on the k-th column of row 5, all within one section. */
    private fun PalimpsestDb.play(
        dimension: DimensionId,
        retention: Retention,
        from: Int,
        to: Int,
    ) {
        val stone = vocabulary.id("minecraft:stone:0")
        val grass = vocabulary.id("minecraft:grass:0")
        val torch = vocabulary.id("minecraft:torch:5")
        val target = also { it.setRetention(dimension, retention) }.dimension(dimension)
        for (k in from..to) {
            val observation = chunk(origin, stone, grass)
            val sections = observation.sections.toMutableList()
            val blocks = sections[4]!!.unpack()
            for (placed in 0..k) blocks[SectionBlocks.index(placed % 16, 1, 5 + placed / 16)] =
                torch.raw
            sections[4] = SectionBlocks.of(blocks)
            target
                .commit(
                    WorldTick(k + 1L),
                    listOf(ChunkObservation(origin, 0, sections, observation.biomes)),
                )
                .await()
        }
    }

    private fun PalimpsestDb.torches(
        dimension: DimensionId,
        retention: Retention,
        tick: Long,
    ): Int {
        val torch = vocabulary.id("minecraft:torch:5")
        val volume =
            also { it.setRetention(dimension, retention) }
                .dimension(dimension)
                .at(WorldTick(tick))
                .volume(origin)
                .result
                .await()!!
        return (0 until 256).count { volume.block(it % 16, 65, it / 16) == torch }
    }

    private fun size(root: Path, dimension: DimensionId) =
        Files.walk(root.resolve("dimensions/${dimension.key}")).use { files ->
            files.filter(Files::isRegularFile).mapToLong(Files::size).sum()
        }

    @Test
    fun `small edits are stored as deltas and every moment reads back`() {
        TestWorld().use { world ->
            world.open().use { it.play(OVERWORLD, Retention.HISTORY, 0, 0) }
            val first = size(world.world, OVERWORLD)
            world.open().use { it.play(OVERWORLD, Retention.HISTORY, 1, edits - 1) }
            val growth = size(world.world, OVERWORLD) - first
            assertTrue(
                growth < (edits - 1) * 120,
                "history grew by $growth bytes for ${edits - 1} one-block edits",
            )
            for (check in 0..2) {
                world.open().use { db ->
                    for (k in 0 until edits) assertEquals(
                        k + 1,
                        db.torches(OVERWORLD, Retention.HISTORY, k + 1L),
                        "tick ${k + 1}",
                    )
                }
                if (check == 1) world.config.cacheDirectory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `latest-only compaction turns deltas back into whole sections`() {
        val nether = DimensionId("nether")
        val latest = Retention.LATEST
        TestWorld().use { world ->
            world.open().use { it.play(nether, latest, 0, 9) }
            world.open().use { it.play(nether, latest, 10, edits - 1) }
            world.open().use { db ->
                assertEquals(edits, db.torches(nether, latest, edits.toLong()))
            }
            world.config.cacheDirectory.toFile().deleteRecursively()
            world.open().use { db ->
                assertEquals(edits, db.torches(nether, latest, edits.toLong()))
            }
        }
    }
}
