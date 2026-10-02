package io.github.fopwoc.mods.palimpsest.benchmark.checkpoint

import io.github.fopwoc.mods.palimpsest.benchmark.BenchmarkWorld
import io.github.fopwoc.mods.palimpsest.map.MapPageKey
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.file.Path
import java.security.MessageDigest

/** Uses APIs shared with 2.2.2, so both versions execute identical refresh workloads. */
internal object CheckpointRefreshTrial {
    private class Case(
        val name: String,
        val lod: Int,
        val position: Int,
        val dense: Boolean = false,
    )

    fun run(directory: Path, log: (String) -> Unit) {
        val cases =
            listOf(
                Case("sparse", 0, 85),
                Case("unsampled", 3, 85),
                Case("coarse", 7, TileRecord.CENTER),
                Case("dense", 0, 0, true),
                Case("varied", 0, 85),
            )
        BenchmarkWorld(directory).use { world ->
            val initial = buildMap {
                for (z in 0 until 32) for (x in 0 until 32) put(
                    TileKey(x, z),
                    TileRecord.solid(0, 1 + (x * 7 + z * 11) % 255, 64),
                )
            }
            world.tree.commit(0, initial)
            var epoch = 0L
            for (case in cases) {
                val key = MapPageKey(0, 0, case.lod)
                val changed =
                    if (case.dense) initial.keys.filter { it.x < 8 && it.z < 8 }
                    else listOf(TileKey(0, 0))
                world.tree.commit(++epoch, initial.mapValues { it.value.withEpoch(epoch) })
                world.pages.clear()
                var previous = checkNotNull(world.pages.latest(key))
                val samples = ArrayList<Long>()
                val digest = MessageDigest.getInstance("SHA-256")
                var reused = 0
                repeat(320) { frame ->
                    epoch++
                    val views = changed.associateWith { tile ->
                        val original = initial.getValue(tile)
                        if (case.dense) TileRecord.solid(epoch, 1 + frame % 255, 64)
                        else
                            original.with(
                                epoch,
                                intArrayOf(case.position),
                                arrayOf(
                                    intArrayOf(1 + frame % 255),
                                    intArrayOf(if (case.name == "varied") frame % 256 else 64),
                                    intArrayOf(if (case.name == "varied") frame % 32 else 0),
                                    intArrayOf(
                                        if (case.name == "varied") frame * 197 % 65536 else 0
                                    ),
                                ),
                            )
                    }
                    world.tree.commit(epoch, views)
                    val started = System.nanoTime()
                    world.pages.invalidateTiles(changed, epoch)
                    val actual = checkNotNull(world.pages.latest(key))
                    val elapsed = System.nanoTime() - started
                    if (frame >= 64) {
                        samples += elapsed
                        if (actual === previous) reused++
                    }
                    val expected = checkNotNull(world.builder.build(key, Long.MAX_VALUE))
                    for (z in 0 until MapPageKey.SIDE) for (x in 0 until MapPageKey.SIDE) {
                        val color = actual.colorAt(x, z)
                        check(color == expected.colorAt(x, z))
                        if (frame >= 64) {
                            digest.update((color ushr 24).toByte())
                            digest.update((color ushr 16).toByte())
                            digest.update((color ushr 8).toByte())
                            digest.update(color.toByte())
                        }
                    }
                    previous = actual
                }
                val sorted = samples.sorted()
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                log(
                    "refresh=${case.name} lod=${case.lod} samples=${samples.size} p50_ns=${sorted[sorted.size / 2]} p99_ns=${sorted[sorted.size * 99 / 100]} reused=$reused pixel_sha256=$hash"
                )
            }
        }
        directory.toFile().deleteRecursively()
    }
}
