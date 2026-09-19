package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.util.Random

/** Reproducible edits over a fixed synthetic 32×32 tile world; one commit per epoch. */
internal object BenchmarkGenerator {
    enum class Pattern {
        SPARSE,
        MIXED,
        ADVERSARIAL,
    }

    const val WORLD_SIDE = 32
    const val EPOCHS_PER_BATCH = 250
    private const val CHANGED_TILES_PER_EPOCH = 16
    private const val CHANGED_CELLS_PER_TILE = 8

    data class Result(val commits: Int, val tilesWritten: Int, val nodesWritten: Int, val nodesPatched: Int, val tilesLinked: Int, val bytes: Long, val elapsedNanos: Long)

    /** The block ids of the world's tiles as first observed. */
    fun initial(x: Int, z: Int): IntArray =
        IntArray(TileRecord.PIXELS) { position ->
            val px = position % TileRecord.SIDE
            val pz = position / TileRecord.SIDE
            1 + ((x * 7 + z * 11 + px / 4 * 3 + pz / 4 * 5) and 255)
        }

    fun append(
        world: BenchmarkWorld,
        pattern: Pattern = Pattern.SPARSE,
        epochs: Int = EPOCHS_PER_BATCH,
    ): Result {
        require(epochs in 1..100_000)
        val started = System.nanoTime()
        var tilesWritten = 0
        var nodesWritten = 0
        var nodesPatched = 0
        var tilesLinked = 0
        var bytes = 0L
        var commits = 0
        val tiles = HashMap<TileKey, IntArray>(WORLD_SIDE * WORLD_SIDE)
        if (world.latestEpoch < 0) {
            val first = HashMap<TileKey, TileRecord>()
            for (z in 0 until WORLD_SIDE) for (x in 0 until WORLD_SIDE) {
                val key = TileKey(x, z)
                val ids = initial(x, z)
                tiles[key] = ids
                first[key] = TileRecord.build(0, ids::get)
            }
            val result = world.tree.commit(0, first)
            tilesWritten += result.tilesWritten
            nodesWritten += result.nodesWritten
            nodesPatched += result.nodesPatched
            tilesLinked += result.tilesLinked
            bytes += result.bytes
            commits++
        } else {
            for (z in 0 until WORLD_SIDE) for (x in 0 until WORLD_SIDE) {
                val key = TileKey(x, z)
                tiles[key] =
                    checkNotNull(world.tree.tile(key, Long.MAX_VALUE))
                        .channel(TileRecord.Channel.BLOCK)
            }
        }
        val firstEpoch = world.latestEpoch + 1
        for (epoch in firstEpoch until firstEpoch + epochs) {
            val random = Random(0x50414C49L xor epoch)
            val changedTiles = HashSet<TileKey>()
            while (changedTiles.size < CHANGED_TILES_PER_EPOCH) {
                changedTiles += TileKey(random.nextInt(WORLD_SIDE), random.nextInt(WORLD_SIDE))
            }
            val changes = HashMap<TileKey, TileRecord>()
            for (key in changedTiles.sortedWith(compareBy(TileKey::z, TileKey::x))) {
                val next = checkNotNull(tiles[key]).copyOf()
                val positions =
                    when (pattern) {
                        Pattern.SPARSE -> sparsePositions(random)
                        Pattern.MIXED -> mixedPositions(random)
                        Pattern.ADVERSARIAL -> setOf((key.x * 17 + key.z * 31) and 255)
                    }
                for (position in positions) {
                    val range = if (pattern == Pattern.MIXED) 255 else 48
                    next[position] = 1 + ((next[position] - 1 + 1 + random.nextInt(range)) and 255)
                }
                tiles[key] = next
                changes[key] = TileRecord.build(epoch, next::get)
            }
            val result = world.tree.commit(epoch, changes)
            tilesWritten += result.tilesWritten
            nodesWritten += result.nodesWritten
            nodesPatched += result.nodesPatched
            tilesLinked += result.tilesLinked
            bytes += result.bytes
            commits++
        }
        return Result(commits, tilesWritten, nodesWritten, nodesPatched, tilesLinked, bytes, System.nanoTime() - started)
    }

    private fun sparsePositions(random: Random): Set<Int> = buildSet {
        while (size < CHANGED_CELLS_PER_TILE) add(random.nextInt(TileRecord.PIXELS))
    }

    private fun mixedPositions(random: Random): Set<Int> = buildSet {
        if (random.nextInt(32) == 0) {
            addAll(0 until TileRecord.PIXELS)
            return@buildSet
        }
        repeat(2 + random.nextInt(4)) {
            val width = 2 + random.nextInt(11)
            val height = 2 + random.nextInt(11)
            val left = random.nextInt(TileRecord.SIDE - width + 1)
            val top = random.nextInt(TileRecord.SIDE - height + 1)
            for (y in top until top + height) for (x in left until left + width) add(
                y * TileRecord.SIDE + x
            )
        }
        repeat(8 + random.nextInt(49)) { add(random.nextInt(TileRecord.PIXELS)) }
    }
}
