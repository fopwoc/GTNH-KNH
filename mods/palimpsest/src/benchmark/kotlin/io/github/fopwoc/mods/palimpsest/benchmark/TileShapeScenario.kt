package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.file.Files
import java.nio.file.Path

/** Bytes per tile for map-like shapes: solid, near-solid, banded terrain, noise, real-ish hills. */
internal object TileShapeScenario {
    data class Result(
        val name: String,
        val tiles: Int,
        val sealedBytes: Long,
        val plainBytes: Long,
        val linked: Int,
    )

    private class Shape(val name: String, val record: (Int) -> TileRecord)

    private const val TILES = 1024
    private const val PLAIN_BYTES_PER_TILE = TileRecord.PIXELS * 6L

    fun run(directory: Path): List<Result> {
        val shapes =
            listOf(
                Shape("uniform") { tile -> TileRecord.solid(0, 1 + (tile * 37 and 255), 64, 0, 1) },
                Shape("near-uniform") { tile ->
                    TileRecord.build(
                        0,
                        { position ->
                            1 +
                                ((tile * 37 + if (position == (tile * 17 and 255)) 1 else 0) and
                                    255)
                        },
                        { 64 },
                        { 0 },
                        { 1 },
                    )
                },
                Shape("terrain-bands") { tile -> bands(tile) },
                Shape("hills") { tile ->
                    TileRecord.build(
                        0,
                        { position -> 1 + (position % 16 / 6 + tile % 3) },
                        { position -> 60 + (position % 16) / 3 + (position / 16) / 4 + tile % 2 },
                        { 0 },
                        { position -> if (position % 16 < 8) 1 else 6 },
                    )
                },
                Shape("varied") { tile ->
                    TileRecord.build(
                        0,
                        { position ->
                            1 + ((tile * 73 + position * 29 + (position / 16) * 17) and 255)
                        },
                        { position -> (position * 7 + tile) and 255 },
                        { 0 },
                        { 1 },
                    )
                },
            )
        return shapes.map { shape ->
            val work = directory.resolve(shape.name)
            val linked =
                BenchmarkWorld(work).use { world ->
                    val changes = HashMap<TileKey, TileRecord>()
                    for (tile in 0 until TILES) changes[TileKey(tile % 32, tile / 32)] =
                        shape.record(tile)
                    val linked = world.tree.commit(0, changes).tilesLinked
                    world.tree.seal()
                    for (tile in 0 until TILES step 97) {
                        check(
                            checkNotNull(world.tree.tile(TileKey(tile % 32, tile / 32), 0))
                                .sameFacts(shape.record(tile))
                        )
                    }
                    linked
                }
            val sealedBytes =
                Files.walk(work).use { paths ->
                    paths
                        .filter { it.fileName.toString().endsWith(".pseg") }
                        .mapToLong(Files::size)
                        .sum()
                }
            Result(shape.name, TILES, sealedBytes, TILES * PLAIN_BYTES_PER_TILE, linked)
        }
    }

    private fun bands(tile: Int): TileRecord =
        TileRecord.build(
            0,
            { position ->
                val z = position / TileRecord.SIDE
                1 + ((tile * 5 + z / 4) and 255)
            },
            { position -> 62 + position / TileRecord.SIDE / 4 },
            { 0 },
            { 1 },
        )
}
