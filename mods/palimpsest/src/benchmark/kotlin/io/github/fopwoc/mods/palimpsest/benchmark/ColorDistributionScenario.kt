package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.nio.file.Path

/** Synthetic map-like color distributions for evaluating the layer codec. */
internal object ColorDistributionScenario {
    data class Result(
        val name: String,
        val tiles: Int,
        val layers: Int,
        val sealedBytes: Long,
        val plainBytes: Long,
        val sampledBytes: Long,
        val sampleNanos: Long,
    )

    private data class Pattern(
        val name: String,
        val colors: (Int) -> Pair<ByteArray, ByteArray?>,
    )

    private const val TILES = 1024
    private val samplePosition = intArrayOf(136)

    fun run(directory: Path, shouldStop: () -> Boolean = { false }): List<Result>? {
        val patterns =
            listOf(
                Pattern("uniform") { tile ->
                    ByteArray(TileLayer.PIXELS) { (tile * 37).toByte() } to null
                },
                Pattern("near-uniform") { tile ->
                    ByteArray(TileLayer.PIXELS) { position ->
                        ((tile * 37 + if (position == (tile * 17 and 255)) 1 else 0) and 255)
                            .toByte()
                    } to null
                },
                Pattern("terrain-bands") { tile -> bands(tile) to null },
                Pattern("varied") { tile ->
                    ByteArray(TileLayer.PIXELS) { position ->
                        ((tile * 73 + position * 29 + (position / TileLayer.SIDE) * 17) and 255)
                            .toByte()
                    } to null
                },
                Pattern("solid-footprint") { tile ->
                    val first = bands(tile)
                    first to
                        first.copyOf().apply {
                            for (z in 4 until 12) for (x in 4 until 12) this[
                                z * TileLayer.SIDE + x] = 200.toByte()
                        }
                },
                Pattern("scattered-solid") { tile ->
                    val first = bands(tile)
                    first to
                        first.copyOf().apply {
                            repeat(64) { index ->
                                this[(index * 37 + tile * 13) and 255] = 200.toByte()
                            }
                        }
                },
                Pattern("scattered-varied") { tile ->
                    val first = bands(tile)
                    first to
                        first.copyOf().apply {
                            repeat(64) { index ->
                                this[(index * 37 + tile * 13) and 255] = (150 + index).toByte()
                            }
                        }
                },
            )
        val results = ArrayList<Result>(patterns.size)
        for (pattern in patterns) {
            if (shouldStop()) return null
            val layers = ArrayList<TileLayer>(TILES * 2)
            val initial = ArrayList<ByteArray>(TILES)
            val latest = ArrayList<ByteArray>(TILES)
            for (tile in 0 until TILES) {
                val key = TileKey(tile and 31, tile ushr 5)
                val (first, second) = pattern.colors(tile)
                layers += TileLayer.full(key, 0, first)
                if (second != null) layers += checkNotNull(TileLayer.changed(key, 1, first, second))
                initial += first
                latest += second ?: first
            }
            val plainBytes =
                12L +
                    TILES * 12L +
                    layers.sumOf { layer ->
                        val colors = layer.colors.size
                        when {
                            colors == TileLayer.PIXELS -> 2 + colors
                            colors <= 31 -> 3 + colors * 2
                            else -> 34 + colors
                        }.toLong()
                    }
            val result =
                TileHistoryStore(directory.resolve(pattern.name)).use { store ->
                    val append = store.append(layers)
                    check(append.layersWritten == layers.size)
                    store.reload()
                    var sampledBytes = 0L
                    val started = System.nanoTime()
                    for (tile in 0 until TILES) {
                        if (shouldStop()) return null
                        val key = TileKey(tile and 31, tile ushr 5)
                        val sample = checkNotNull(store.readSamples(key, 1, samplePosition))
                        check(sample.colors[0] == latest[tile][samplePosition[0]])
                        sampledBytes += sample.bytesRead
                    }
                    val sampleNanos = System.nanoTime() - started
                    for (tile in 0 until TILES) {
                        val key = TileKey(tile and 31, tile ushr 5)
                        check(store.read(key, 0)?.colors?.contentEquals(initial[tile]) == true)
                        check(store.read(key, 1)?.colors?.contentEquals(latest[tile]) == true)
                    }
                    Result(
                        pattern.name,
                        TILES,
                        layers.size,
                        append.bytesAdded,
                        plainBytes,
                        sampledBytes,
                        sampleNanos,
                    )
                }
            check(result.sealedBytes <= result.plainBytes)
            results += result
        }
        return results
    }

    private fun bands(tile: Int): ByteArray =
        ByteArray(TileLayer.PIXELS) { position ->
            (20 + (tile and 7) * 4 + (position / TileLayer.SIDE) / 4).toByte()
        }
}
