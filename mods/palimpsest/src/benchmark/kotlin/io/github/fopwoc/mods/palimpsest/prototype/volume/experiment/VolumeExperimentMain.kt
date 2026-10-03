package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.ChunkVolume
import io.github.fopwoc.mods.palimpsest.prototype.volume.model.Vocabulary
import io.github.fopwoc.mods.palimpsest.prototype.volume.save.LegacySave
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

/**
 * `./gradlew volumeExperiment --args="<GTNH saves directory>"`: full 3D history next to the 2.4
 * surface tree, on real GTNH saves. Reads the saves without modifying them; all stores and the
 * unpacked backup live under `build/volume-experiment`.
 */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
fun main(args: Array<String>) {
    val saves = Path.of(args.single())
    val out = Path.of("build/volume-experiment")
    if (out.exists()) out.deleteRecursively()
    out.createDirectories()
    val vocabulary = Vocabulary()

    section("Exploration: every populated chunk of `rostik`, 64 chunks per commit")
    val rostik = LegacySave(saves.resolve("rostik"), vocabulary)
    PairedStores(out.resolve("exploration"), vocabulary).use { stores ->
        val result = ExplorationScenario(rostik, stores).run()
        println("chunks ${result.chunks}, commits ${result.commits}, vocabulary ${vocabulary.size} block identities")
        sizes("2.4 surface history", result.surfaceBytes, result.chunks)
        sizes("3D volume history", result.volumeBytes, result.chunks)
        println("3D ÷ surface: %.1f×".format(result.volumeBytes.toDouble() / result.surfaceBytes))
        println("section codec sample (${result.sampledSections} sections): range-coded %.0f B, deflate %.0f B per section"
            .format(result.rangeCodedSample.toDouble() / result.sampledSections, result.deflatedSample.toDouble() / result.sampledSections))
        commitTimes(stores)
    }

    section("Real history: `New World` backup (2025-12-14) → current save (2026-06-13)")
    val backup = unpack(saves.resolve("New World-20251214-020051.zip"), out.resolve("backup-save"))
    PairedStores(out.resolve("real"), vocabulary).use { stores ->
        val result = RealHistoryScenario(LegacySave(backup, vocabulary), LegacySave(saves.resolve("New World"), vocabulary), stores).run()
        for ((label, phase) in listOf("backup, first sight" to result.initial, "revisited chunks" to result.revisited, "newly explored" to result.explored)) {
            println("%-22s %5d chunks, %5d changed versions, %6d sections written | 2.4 +%s | 3D +%s"
                .format(label, phase.chunks, phase.versions, phase.sections, human(phase.surfaceBytes), human(phase.volumeBytes)))
        }
    }

    section("Simulated play: 2,000 minutes on real `rostik` terrain around spawn")
    val (spawnX, spawnZ) = rostik.spawn
    val radius = 20
    val area = (spawnX shr 4) - radius to (spawnZ shr 4) - radius
    val world = HashMap<TileKey, ChunkVolume>()
    for (region in rostik.regions()) for ((key, volume) in rostik.volumes(region))
        if (key.x - area.first in 0 until radius * 2 && key.z - area.second in 0 until radius * 2) world[key] = volume
    PairedStores(out.resolve("play"), vocabulary).use { stores ->
        stores.commit(1, world.toMap())
        val initialSurface = stores.surfaceBytes()
        val initialVolume = stores.volumes.bytes
        println("area ${world.size} chunks; first sight: 2.4 ${human(initialSurface)}, 3D ${human(initialVolume)}")
        stores.resetTimings()
        val play = PlayScenario(world, vocabulary, stores, spawnX, spawnZ).run(firstEpoch = 2, commits = 2000)
        println("${play.commits} commits, ${play.chunkVersions} changed chunk versions, " +
            "${play.tilesWritten} surface tiles written, ${play.sectionsWritten} sections written")
        println("history added: 2.4 +${human(play.surfaceBytes)} (%.0f B/commit), 3D +${human(play.volumeBytes)} (%.0f B/commit)"
            .format(play.surfaceBytes.toDouble() / play.commits, play.volumeBytes.toDouble() / play.commits))
        commitTimes(stores)

        section("Historical viewports: 32×32 chunks (512 px) at random moments of the play history")
        val reads = ViewportReads(stores, vocabulary, area.first, area.second,
            area.first + radius * 2 - 1, area.second + radius * 2 - 1, 1L..2001L)
        for (line in reads.run()) println("%-58s %s".format(line.label, line.timings))
        println("3D surface vs 2.4 tiles: ${reads.mismatches} mismatches in ${reads.compared} chunks")
    }
}

private fun section(title: String) {
    println()
    println("## $title")
}

private fun sizes(label: String, bytes: Long, chunks: Int) =
    println("%-22s %10s  %6.0f B/chunk".format(label, human(bytes), bytes.toDouble() / chunks))

private fun commitTimes(stores: PairedStores) {
    println("commit, 2.4 tree:        ${Timings(stores.surfaceTimes)}")
    println("commit, 3D store:        ${Timings(stores.volumeTimes)}")
    println("surface scan of volumes: ${Timings(stores.scanTimes)}")
}

/** Unpacks the overworld of a zipped save: `level.dat` and `region/`. */
private fun unpack(zip: Path, target: Path): Path {
    ZipFile(zip.toFile()).use { file ->
        for (entry in file.entries()) {
            val name = entry.name
            if (name != "level.dat" && !(name.startsWith("region/") && name.endsWith(".mca"))) continue
            val destination = target.resolve(name)
            destination.parent.createDirectories()
            file.getInputStream(entry).use { Files.copy(it, destination) }
        }
    }
    return target
}
