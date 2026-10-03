package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.ChunkVolume
import io.github.fopwoc.mods.palimpsest.prototype.volume.model.Vocabulary
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import kotlin.random.Random

/**
 * Simulated GTNH play on real terrain, one commit per simulated minute: strip-mining tunnels near
 * the player, a base of growing buildings with GT machines inside, a quarry eaten one layer per
 * visit, and biome changes for bees. Synthetic edits, real chunks underneath them.
 */
class PlayScenario(
    world: MutableMap<TileKey, ChunkVolume>,
    private val vocabulary: Vocabulary,
    private val stores: PairedStores,
    private val baseX: Int,
    private val baseZ: Int,
    seed: Long = 42,
) {
    class Result(
        val commits: Int,
        val chunkVersions: Long,
        val tilesWritten: Long,
        val sectionsWritten: Long,
        val surfaceBytes: Long,
        val volumeBytes: Long,
    )

    private val random = Random(seed)
    private val edits = WorldEdits(world, vocabulary)
    private val materials =
        listOf("minecraft:stonebrick:0", "minecraft:planks:0", "minecraft:cobblestone:0",
            "gregtech:gt.blockcasings:2", "minecraft:brick_block:0").map(vocabulary::id)
    private val glass = vocabulary.id("minecraft:glass:0")
    private val air = 0

    private class Building(val x: Int, val z: Int, val width: Int, val depth: Int, val height: Int,
        val floor: Int, val material: Int) {
        var level = 0
    }

    private val buildings = List(12) {
        val x = baseX + random.nextInt(-40, 32)
        val z = baseZ + random.nextInt(-40, 32)
        Building(x, z, random.nextInt(5, 13), random.nextInt(5, 13), random.nextInt(4, 15),
            edits.ground(x, z) + 1, materials.random(random))
    }.filter { it.floor > 0 }
    private val quarryX = baseX + 48
    private val quarryZ = baseZ - 8
    private var quarryY = edits.ground(quarryX + 8, quarryZ + 8)
    private var playerX = baseX
    private var playerZ = baseZ

    fun run(firstEpoch: Long, commits: Int): Result {
        val versions = stores.chunkVersions
        val tiles = stores.tilesWritten
        val sections = stores.sectionsWritten
        val surface = stores.surfaceBytes()
        val volume = stores.volumes.bytes
        for (minute in 0 until commits) {
            if (minute % 50 == 0) {
                playerX = baseX
                playerZ = baseZ
            }
            val roll = random.nextDouble()
            when {
                roll < 0.55 -> tunnel()
                roll < 0.85 -> build()
                roll < 0.95 -> quarry()
                else -> bees()
            }
            if (random.nextDouble() < 0.2) tunnel()
            stores.commit(firstEpoch + minute, edits.flush())
        }
        return Result(
            commits,
            stores.chunkVersions - versions,
            stores.tilesWritten - tiles,
            stores.sectionsWritten - sections,
            stores.surfaceBytes() - surface,
            stores.volumes.bytes - volume,
        )
    }

    private fun tunnel() {
        var x = playerX + random.nextInt(-48, 49)
        var z = playerZ + random.nextInt(-48, 49)
        val y = random.nextInt(12, 61)
        val (dx, dz) = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).random(random)
        repeat(random.nextInt(8, 41)) {
            for (dy in 0..1) if (edits.block(x, y + dy, z) != air) edits.set(x, y + dy, z, air)
            x += dx
            z += dz
        }
        playerX = x
        playerZ = z
    }

    private fun build() {
        val building = buildings.filter { it.level < it.height }.randomOrNull(random) ?: return tunnel()
        val y = building.floor + building.level
        for (x in building.x until building.x + building.width)
            for (z in building.z until building.z + building.depth) {
                val edge = x == building.x || z == building.z ||
                    x == building.x + building.width - 1 || z == building.z + building.depth - 1
                when {
                    building.level == 0 -> edits.set(x, y, z, building.material)
                    edge -> edits.set(x, y, z, if ((x + z) % 3 == 0 && building.level % 4 == 2) glass else building.material)
                }
            }
        if (building.level > 0 && random.nextDouble() < 0.4) repeat(random.nextInt(1, 5)) {
            val x = building.x + random.nextInt(1, building.width - 1)
            val z = building.z + random.nextInt(1, building.depth - 1)
            edits.set(x, building.floor + 1, z, vocabulary.id("gregtech:gt.blockmachines@${random.nextInt(1000, 1100)}"))
        }
        building.level++
    }

    private fun quarry() {
        if (quarryY < 6) return tunnel()
        for (x in quarryX until quarryX + 16) for (z in quarryZ until quarryZ + 16)
            if (edits.block(x, quarryY, z) != air) edits.set(x, quarryY, z, air)
        quarryY--
    }

    private fun bees() {
        val x = baseX + random.nextInt(-32, 32)
        val z = baseZ + random.nextInt(-32, 32)
        for (dx in 0 until 3) for (dz in 0 until 3) edits.biome(x + dx, z + dz, JUNGLE)
    }

    private companion object {
        const val JUNGLE = 21
    }
}
