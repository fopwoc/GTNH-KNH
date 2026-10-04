package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.history.BlockClass
import io.github.fopwoc.mods.palimpsest.history.DimensionHistory
import io.github.fopwoc.mods.palimpsest.history.HistoryTiles
import io.github.fopwoc.mods.palimpsest.history.WorldHistory
import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.palimpsest.db.Biomes
import io.github.fopwoc.palimpsest.db.BlockKind
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.SectionBlocks
import java.nio.file.Path
import java.time.Duration
import kotlin.test.assertNotNull

/**
 * A real history database under [directory] for one dimension, drawing with [blocks]. Chunks are
 * flat: one layer of a block at y = 64.
 */
internal class TestHistory(directory: Path, val blocks: BlockTable) : AutoCloseable {
    val world: WorldHistory =
        assertNotNull(
            WorldHistory.open(directory.resolve("history"), directory.resolve("cache")) { null }
        )
    val dimension =
        DimensionHistory(
            world,
            DimensionId("overworld"),
            blocks,
            { null },
            { Duration.ofSeconds(60) },
        )

    fun tiles() = HistoryTiles(dimension)

    /** Stages chunk ([x], [z]) as a flat layer of [identity], coloured [color] if it is new. */
    fun stage(x: Int, z: Int, identity: String, color: Int = 0x808080) {
        val id = dimension.idOf(identity, BlockClass(BlockKind.SOLID, color, 0)).raw
        val layer =
            IntArray(SectionBlocks.VOLUME) { at ->
                if (at < SectionBlocks.SIDE * SectionBlocks.SIDE) id else 0
            }
        val sections =
            List(SECTIONS) { index -> if (index == SECTION) SectionBlocks.of(layer) else null }
        dimension.stage(
            ChunkObservation(
                ChunkPos(x, z),
                0,
                sections,
                Biomes.Columns(IntArray(Biomes.Columns.COLUMNS) { 1 }),
            )
        )
    }

    /** Commits what is staged at [tick] and waits until history has it. */
    fun commit(tick: Long) {
        assertNotNull(dimension.commitNow(tick)).get()
    }

    override fun close() {
        world.closeAsync().get()
    }

    companion object {
        private const val SECTIONS = 16
        private const val SECTION = 4

        inline fun with(prefix: String, test: (TestHistory) -> Unit) =
            TestBlocks.withDirectory(prefix) { directory ->
                TestHistory(directory, TestBlocks.table(directory)).use(test)
            }
    }
}
