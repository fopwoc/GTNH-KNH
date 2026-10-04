package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.Vocabulary
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Arbitrary jumps through history: [samples] random (epoch, viewport) pairs of [side]×[side] chunks
 * inside the played area. The 2.4 path reads surface tiles from the tree; the 3D path rebuilds each
 * chunk's volume at that epoch and scans it from a ceiling. Rendering is identical after either and
 * is excluded. Every surface view from 3D is checked against the 2.4 tiles.
 */
class ViewportReads(
    private val stores: PairedStores,
    private val vocabulary: Vocabulary,
    private val minX: Int,
    private val minZ: Int,
    private val maxX: Int,
    private val maxZ: Int,
    private val epochs: LongRange,
    private val side: Int = 32,
    samples: Int = 48,
    seed: Long = 7,
) {
    private class Request(val epoch: Long, val x0: Int, val z0: Int)

    class Line(val label: String, val timings: Timings)

    private val random = Random(seed)
    private val requests =
        List(samples) {
            Request(
                random.nextLong(epochs.first, epochs.last + 1),
                random.nextInt(minX, maxX - side + 2),
                random.nextInt(minZ, maxZ - side + 2),
            )
        }

    var mismatches = 0
        private set

    var compared = 0
        private set

    fun run(): List<Line> {
        val lines = mutableListOf<Line>()
        lines += Line("2.4 surface tiles, fresh tree per view", surfaceReads(cold = true))
        lines += Line("2.4 surface tiles, warm tree", surfaceReads(cold = false))
        Executors.newFixedThreadPool(4).use { pool ->
            for (ceiling in listOf(255, 40)) for (threads in listOf(1, 4)) for (cold in
                listOf(true, false)) {
                val label =
                    "3D volume → scan from y=$ceiling, $threads thread${if (threads > 1) "s" else ""}, " +
                        if (cold) "cleared cache" else "warm cache"
                lines += Line(label, volumeReads(ceiling, threads, cold, pool))
            }
        }
        return lines
    }

    private fun keys(request: Request): List<TileKey> =
        List(side * side) { TileKey(request.x0 + it % side, request.z0 + it / side) }

    private fun surfaceReads(cold: Boolean): Timings {
        val samples = mutableListOf<Long>()
        if (!cold) requests.forEach { stores.tree.tiles(it.x0, it.z0, side, it.epoch) }
        for (request in requests) {
            if (cold) stores.reopenSurface()
            timed(samples) { stores.tree.tiles(request.x0, request.z0, side, request.epoch) }
        }
        return Timings(samples)
    }

    private fun volumeReads(
        ceiling: Int,
        threads: Int,
        cold: Boolean,
        pool: java.util.concurrent.ExecutorService,
    ): Timings {
        val kinds = vocabulary.kinds()
        val samples = mutableListOf<Long>()
        fun view(request: Request): Array<TileRecord?> {
            val keys = keys(request)
            val out = arrayOfNulls<TileRecord>(keys.size)
            val stripe = (keys.size + threads - 1) / threads
            val tasks =
                (0 until threads).map { part ->
                    Callable {
                        for (at in part * stripe until minOf(keys.size, (part + 1) * stripe)) out[
                            at] =
                            stores.volumes.volume(keys[at], request.epoch)?.let {
                                surface(it, kinds, request.epoch, ceiling)
                            }
                    }
                }
            if (threads == 1) tasks.single().call() else pool.invokeAll(tasks).forEach { it.get() }
            return out
        }
        if (!cold) requests.forEach(::view)
        for (request in requests) {
            if (cold) stores.volumes.clearCache()
            val tiles = timed(samples) { view(request) }
            if (ceiling == 255 && threads == 1 && cold) verify(request, tiles)
        }
        return Timings(samples)
    }

    private fun verify(request: Request, tiles: Array<TileRecord?>) {
        val expected = stores.tree.tiles(request.x0, request.z0, side, request.epoch)
        for (at in tiles.indices) {
            compared++
            val left = expected[at]
            val right = tiles[at]
            if ((left == null) != (right == null) || (left != null && !left.sameFacts(right!!)))
                mismatches++
        }
    }
}
