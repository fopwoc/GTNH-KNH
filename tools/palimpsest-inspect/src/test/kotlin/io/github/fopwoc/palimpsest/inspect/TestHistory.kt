package io.github.fopwoc.palimpsest.inspect

import io.github.fopwoc.palimpsest.db.Biomes
import io.github.fopwoc.palimpsest.db.BlockId
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.DbConfig
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.OpenResult
import io.github.fopwoc.palimpsest.db.PalimpsestDb
import io.github.fopwoc.palimpsest.db.SectionBlocks
import io.github.fopwoc.palimpsest.db.WorldTick
import io.github.fopwoc.palimpsest.inspect.history.NameKinds
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively

/** A real history written into a temporary folder, deleted after [use]. */
@OptIn(ExperimentalPathApi::class)
class TestHistory : AutoCloseable {
    val root: Path = createTempDirectory("palimpsest-inspect-test-")
    val history: Path = root.resolve("history")

    /** Writes commits with [write], then closes the database so the folder is complete. */
    fun write(write: Writer.() -> Unit) {
        val config = DbConfig(root.resolve("cache"), NameKinds, { _, _, _, _ -> })
        val db = (PalimpsestDb.open(history, config) as OpenResult.Opened).db
        db.use { Writer(it).write() }
    }

    override fun close() {
        root.deleteRecursively()
    }

    class Writer(val db: PalimpsestDb) {
        private val dimension = db.dimension(OVERWORLD)

        fun id(identity: String): BlockId = db.vocabulary.id(identity)

        /** Commits [chunks] at [tick], each given as its blocks by position. */
        fun commit(tick: Long, vararg chunks: Pair<ChunkPos, (x: Int, y: Int, z: Int) -> BlockId>) {
            dimension
                .commit(WorldTick(tick), chunks.map { (pos, block) -> observation(pos, block) })
                .get()
        }

        private fun observation(pos: ChunkPos, block: (Int, Int, Int) -> BlockId) =
            ChunkObservation(
                pos,
                0,
                List(SECTIONS) { section ->
                    val blocks = IntArray(SectionBlocks.VOLUME)
                    for (y in 0 until 16) for (z in 0 until 16) for (x in 0 until 16) blocks[
                        SectionBlocks.index(x, y, z)] = block(x, section * 16 + y, z).raw
                    if (blocks.all { it == 0 }) null else SectionBlocks.of(blocks)
                },
                Biomes.Columns(IntArray(Biomes.Columns.COLUMNS) { 1 }),
            )
    }

    companion object {
        val OVERWORLD = DimensionId("dim0")
        private const val SECTIONS = 16
    }
}
