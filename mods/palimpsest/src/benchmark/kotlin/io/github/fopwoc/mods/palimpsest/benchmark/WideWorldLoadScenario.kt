package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.map.MapPageKey
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.file.Files
import java.nio.file.Path

/** Measures page builds at every zoom over a dense world plus sparse distant observations. */
internal object WideWorldLoadScenario {
    data class Level(
        val lod: Int,
        val coveredTiles: Long,
        val nodesRead: Long,
        val tilesDecoded: Long,
        val coldPageNanos: Long,
        val warmPageNanos: Long,
        val historicalPageNanos: Long,
        val freshOpenPageNanos: Long,
    )

    data class Result(val tiles: Int, val segmentBytes: Long, val generateNanos: Long, val levels: List<Level>)

    private const val BATCH_SIDE = 32

    fun run(directory: Path, side: Int, shouldStop: () -> Boolean, onProgress: (String) -> Unit): Result? {
        require(side >= MapPageKey.SIDE && side % BATCH_SIDE == 0)
        var writtenTiles = 0
        var epoch = 0L
        val generateStart = System.nanoTime()
        BenchmarkWorld(directory).use { world ->
            for (batchZ in 0 until side / BATCH_SIDE) for (batchX in 0 until side / BATCH_SIDE) {
                if (shouldStop()) return null
                val changes = HashMap<TileKey, TileRecord>(BATCH_SIDE * BATCH_SIDE)
                for (localZ in 0 until BATCH_SIDE) for (localX in 0 until BATCH_SIDE) {
                    val x = batchX * BATCH_SIDE + localX
                    val z = batchZ * BATCH_SIDE + localZ
                    changes[TileKey(x, z)] = tile(x, z, epoch)
                }
                check(world.tree.commit(epoch, changes).tilesWritten == changes.size)
                epoch++
                writtenTiles += changes.size
                world.tree.sealIfDue()
                onProgress("Wide world: $writtenTiles/${side * side} dense tiles")
            }
            val distant = HashSet<TileKey>()
            for (lod in 7..MapPageKey.MAX_LOD) {
                val stride = 1 shl (lod - 4)
                for (z in 0 until MapPageKey.SIDE) for (x in 0 until MapPageKey.SIDE) {
                    val key = TileKey(x * stride, z * stride)
                    if (key.x >= side || key.z >= side) distant += key
                }
            }
            for (batch in distant.chunked(1024)) {
                if (shouldStop()) return null
                check(world.tree.commit(epoch, batch.associateWith { tile(it.x, it.z, epoch) }).tilesWritten == batch.size)
                epoch++
                writtenTiles += batch.size
            }
            onProgress("Wide world: $writtenTiles tiles including distant samples")
            // One late edit at the origin, so the historical page at epoch 0 differs from latest.
            val origin = checkNotNull(world.tree.tile(TileKey(0, 0), Long.MAX_VALUE))
            val edited = origin.with(epoch, intArrayOf(136), arrayOf(intArrayOf(99), intArrayOf(64), intArrayOf(0), intArrayOf(1)))
            check(world.tree.commit(epoch, mapOf(TileKey(0, 0) to edited)).tilesWritten == 1)
            world.tree.seal()
        }
        val generateNanos = System.nanoTime() - generateStart
        if (shouldStop()) return null
        val segmentBytes = Files.walk(directory).use { paths -> paths.filter { it.fileName.toString().endsWith(".pseg") }.mapToLong(Files::size).sum() }
        val levels = ArrayList<Level>()
        BenchmarkWorld(directory).use { world ->
            for (lod in 4..MapPageKey.MAX_LOD) {
                if (shouldStop()) return null
                onProgress("Wide world: LOD $lod/${MapPageKey.MAX_LOD}")
                val pageKey = MapPageKey(0, 0, lod)
                val nodesBefore = world.tree.nodesRead()
                val decodedBefore = world.tree.tilesDecoded()
                val start = System.nanoTime()
                val latest = checkNotNull(world.pages.latest(pageKey))
                val coldNanos = System.nanoTime() - start
                val nodesRead = world.tree.nodesRead() - nodesBefore
                val tilesDecoded = world.tree.tilesDecoded() - decodedBefore
                val warmStart = System.nanoTime()
                check(world.pages.latest(pageKey) === latest)
                val warmNanos = System.nanoTime() - warmStart
                val historyStart = System.nanoTime()
                val historical = checkNotNull(world.pages.historical(pageKey, 0))
                val historicalNanos = System.nanoTime() - historyStart
                check(latest.colorAt(0, 0) == BenchmarkWorld.shown(99)) { "latest origin ${latest.colorAt(0, 0).toString(16)}" }
                check(historical.colorAt(0, 0) == BenchmarkWorld.shown(tile(0, 0, 0).block(136))) { "historical origin ${historical.colorAt(0, 0).toString(16)}" }
                val freshStart = System.nanoTime()
                BenchmarkWorld(directory).use { fresh -> checkNotNull(fresh.pages.latest(pageKey)) }
                val freshNanos = System.nanoTime() - freshStart
                val pageTileSide = MapPageKey.BASE_TILES.toLong() shl lod
                levels += Level(lod, pageTileSide * pageTileSide, nodesRead, tilesDecoded, coldNanos, warmNanos, historicalNanos, freshNanos)
            }
        }
        return Result(writtenTiles, segmentBytes, generateNanos, levels)
    }

    private fun tile(x: Int, z: Int, epoch: Long): TileRecord =
        TileRecord.build(epoch, { 1 + ((x * 31 + z * 17 + it) and 255) }, { 64 }, { 0 }, { 1 })
}
