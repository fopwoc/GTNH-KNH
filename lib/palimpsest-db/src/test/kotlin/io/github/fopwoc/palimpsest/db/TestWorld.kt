package io.github.fopwoc.palimpsest.db

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.assertIs

/** A temporary world folder and helpers that build chunks from a height function. */
class TestWorld : AutoCloseable {
    val root: Path = createTempDirectory("palimpsest-db-")
    val world: Path = root.resolve("world")
    val config =
        DbConfig(
            cacheDirectory = root.resolve("cache"),
            blockKinds = { BlockKind.SOLID },
            log = { level, tag, message, error ->
                if (level >= LogLevel.WARN)
                    System.err.println("[$level/$tag] $message ${error ?: ""}")
            },
            interactiveThreads = 2,
            backgroundThreads = 4,
        )

    fun open(at: Path = world): PalimpsestDb =
        assertIs<OpenResult.Opened>(PalimpsestDb.open(at, config)).db

    override fun close() {
        root.toFile().deleteRecursively()
    }

    companion object {
        val OVERWORLD = DimensionId("overworld")

        /**
         * Stone up to [height] with [top] on the surface, plus an optional block placed at [extra].
         */
        fun chunk(
            pos: ChunkPos,
            stone: BlockId,
            top: BlockId,
            height: (Int, Int) -> Int = { _, _ -> 64 },
            extra: Pair<Triple<Int, Int, Int>, BlockId>? = null,
        ): ChunkObservation {
            val sections =
                List(16) { section ->
                    val blocks = IntArray(SectionBlocks.VOLUME)
                    for (y in 0 until 16) for (z in 0 until 16) for (x in 0 until 16) {
                        val worldY = section * 16 + y
                        val surface = height(pos.x * 16 + x, pos.z * 16 + z)
                        blocks[SectionBlocks.index(x, y, z)] =
                            when {
                                worldY < surface -> stone.raw
                                worldY == surface -> top.raw
                                else -> 0
                            }
                    }
                    extra?.let { (at, block) ->
                        if (at.second shr 4 == section)
                            blocks[SectionBlocks.index(at.first, at.second and 15, at.third)] =
                                block.raw
                    }
                    if (blocks.all { it == 0 }) null else SectionBlocks.of(blocks)
                }
            return ChunkObservation(
                pos,
                0,
                sections,
                Biomes.Columns(IntArray(256) { if (it < 128) 1 else 4 }),
            )
        }

        fun <T> java.util.concurrent.CompletableFuture<T>.await(): T = get(30, TimeUnit.SECONDS)

        /** The dimension once its history is loaded, for tests that read its timeline directly. */
        fun Dimension.loaded(): Dimension = also { ready.await() }
    }
}
