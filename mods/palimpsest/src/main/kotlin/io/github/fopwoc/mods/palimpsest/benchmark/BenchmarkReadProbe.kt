package io.github.fopwoc.mods.palimpsest.benchmark

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
    )

    fun run(world: BenchmarkWorld, epoch: Long, left: Int, top: Int): Result {
        val times = LongArray(SAMPLES)
        repeat(SAMPLES) { sample ->
            times[sample] = BenchmarkTileRenderer.read(world, epoch, left, top).elapsedNanos
        }
        times.sort()
        return Result(
            epoch,
            left,
            top,
            times[SAMPLES / 2] / 1_000,
            times[(SAMPLES * 95 + 99) / 100 - 1] / 1_000,
            times.last() / 1_000,
        )
    }
}
