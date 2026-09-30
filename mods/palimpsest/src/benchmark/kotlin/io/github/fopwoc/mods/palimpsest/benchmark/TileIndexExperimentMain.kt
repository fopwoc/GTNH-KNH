package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.measureNanoTime

/** Compare resolved tile windows with individual lookups over exactly the same facts and roots. */
fun main(args: Array<String>) {
    require(args.size <= 1)
    val epochs = args.singleOrNull()?.toInt() ?: 2000
    require(epochs in 1..100_000)
    val directory = Files.createTempDirectory("palimpsest-tile-index-")
    try {
        for (pattern in BenchmarkGenerator.Pattern.entries) {
            val work = directory.resolve(pattern.name.lowercase())
            BenchmarkWorld(work).use { world ->
                BenchmarkGenerator.append(world, pattern, epochs)
                world.tree.seal()
                val epoch = epochs / 2L
                val indexed = world.tree.tiles(8, 8, 8, epoch)
                for (offset in indexed.indices) {
                    check(
                        indexed[offset] ==
                            world.tree.tile(TileKey(8 + offset % 8, 8 + offset / 8), epoch)
                    )
                }
                println("case=$pattern epochs=$epochs tiles=64 persisted_index_bytes=0")
                for (useIndex in listOf(false, true)) {
                    repeat(200) { readWindow(world.tree, epoch, useIndex) }
                    var digest = 0L
                    val warm =
                        LongArray(600) {
                            measureNanoTime { digest += readWindow(world.tree, epoch, useIndex) }
                        }
                    val cold = coldWindow(work, epoch, useIndex)
                    println(
                        "indexed=$useIndex warm_p50_ns=${percentile(warm, 50)} warm_p99_ns=${percentile(warm, 99)} cold_p50_ns=${percentile(cold, 50)} cold_p99_ns=${percentile(cold, 99)} digest=$digest"
                    )
                }
            }
        }
    } finally {
        Files.walk(directory).use { files ->
            files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }
}

private fun coldWindow(directory: Path, epoch: Long, indexed: Boolean): LongArray {
    // The writer must be closed before opening another store, so use a distinct read fixture.
    val copy = Files.createTempDirectory("palimpsest-tile-index-cold-")
    try {
        Files.list(directory).use { files ->
            files
                .filter {
                    it.fileName.toString().endsWith(".pseg") ||
                        it.fileName.toString().startsWith("segments.")
                }
                .forEach {
                    Files.copy(it, copy.resolve(it.fileName))
                }
        }
        repeat(50) { MapTree(copy, BenchmarkWorld.MACHINE).use { readWindow(it, epoch, indexed) } }
        var digest = 0L
        val timings =
            LongArray(40) {
                MapTree(copy, BenchmarkWorld.MACHINE).use { tree ->
                    measureNanoTime { digest += readWindow(tree, epoch, indexed) }
                }
            }
        check(digest > 0)
        return timings
    } finally {
        Files.walk(copy).use { files ->
            files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }
}

private fun readWindow(tree: MapTree, epoch: Long, indexed: Boolean): Long {
    if (indexed) return tree.tiles(8, 8, 8, epoch).sumOf { it?.block(0)?.toLong() ?: 0 }
    var digest = 0L
    for (z in 8 until 16) for (x in 8 until 16) digest +=
        tree.tile(TileKey(x, z), epoch)?.block(0) ?: 0
    return digest
}

private fun percentile(values: LongArray, percentile: Int): Long =
    values.sorted()[((values.size - 1) * percentile) / 100]
