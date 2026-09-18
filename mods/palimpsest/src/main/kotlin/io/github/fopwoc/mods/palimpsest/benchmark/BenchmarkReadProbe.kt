package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore

/** Repeated warm-cache reads of one viewport, including tile reconstruction and RGBA packaging. */
internal object BenchmarkReadProbe {
    const val SAMPLES = 120

    data class Result(
        val epoch: Long,
        val left: Int,
        val top: Int,
        val medianMicros: Long,
        val p95Micros: Long,
        val maxMicros: Long,
        val maxVisited: Int,
    )

    fun run(store: TileHistoryStore, epoch: Long, left: Int, top: Int): Result {
        val times = LongArray(SAMPLES)
        var maxVisited = 0
        repeat(SAMPLES) { sample ->
            val read = BenchmarkTileRenderer.read(store, epoch, left, top)
            times[sample] = read.elapsedNanos
            maxVisited = maxOf(maxVisited, read.visitedLayers)
        }
        times.sort()
        return Result(
            epoch,
            left,
            top,
            times[SAMPLES / 2] / 1_000,
            times[(SAMPLES * 95 + 99) / 100 - 1] / 1_000,
            times.last() / 1_000,
            maxVisited,
        )
    }
}
