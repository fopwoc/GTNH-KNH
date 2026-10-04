package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.Vocabulary
import io.github.fopwoc.mods.palimpsest.prototype.volume.save.LegacySave
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

private const val PER_COMMIT = 256
private const val READ_RADIUS = 20

/**
 * `./gradlew volumeSnapshotExperiment --args="<world directory>"`: a long-lived world's overworld
 * and Nether through both stores, costs by how long players stayed, then historical-viewport reads
 * centred on the most inhabited chunk. Reads the world without modifying it.
 */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
fun main(args: Array<String>) {
    val vocabulary = Vocabulary()
    val world = LegacySave(Path.of(args.single()), vocabulary)
    val out = Path.of("build/volume-snapshot")
    if (out.exists()) out.deleteRecursively()
    out.createDirectories()

    for ((label, dimension) in listOf("Overworld" to "region", "Nether" to "DIM-1/region")) {
        println()
        println("## $label")
        PairedStores(out.resolve(label.lowercase()), vocabulary, cacheSections = 65_536).use {
            stores ->
            val result = SnapshotScenario(world, dimension, stores, PER_COMMIT).run()
            val chunks = result.costs.values.sumOf { it.chunks }
            println("$chunks chunks, vocabulary ${vocabulary.size} block identities")
            println(
                "whole dimension: 2.4 surface %s (%.0f B/chunk), 3D %s (%.0f B/chunk), 3D ÷ surface %.1f×"
                    .format(
                        human(result.surfaceBytes),
                        result.surfaceBytes.toDouble() / chunks,
                        human(result.volumeBytes),
                        result.volumeBytes.toDouble() / chunks,
                        result.volumeBytes.toDouble() / result.surfaceBytes,
                    )
            )
            println(
                "by time players spent nearby (surface = full tile records, 3D = new pack bytes):"
            )
            for ((category, cost) in result.costs) {
                println(
                    "  %-24s %7d chunks | surface %6.0f B/chunk | 3D %6.0f B/chunk | 3D total %s"
                        .format(
                            category.label,
                            cost.chunks,
                            cost.surfaceRecordBytes.toDouble() / cost.chunks,
                            cost.volumeBytes.toDouble() / cost.chunks,
                            human(cost.volumeBytes),
                        )
                )
            }
            println("commit of $PER_COMMIT chunks, 2.4 tree: ${Timings(stores.surfaceTimes)}")
            println("commit of $PER_COMMIT chunks, 3D store: ${Timings(stores.volumeTimes)}")
            if (dimension != "region") return@use

            val center = result.busiest
            println()
            println(
                "## Viewports centred on the busiest base, chunk ${center.x}, ${center.z} (32×32 chunks, present moment)"
            )
            val reads =
                ViewportReads(
                    stores,
                    vocabulary,
                    center.x - READ_RADIUS,
                    center.z - READ_RADIUS,
                    center.x + READ_RADIUS - 1,
                    center.z + READ_RADIUS - 1,
                    result.lastEpoch..result.lastEpoch,
                )
            for (line in reads.run()) println("%-58s %s".format(line.label, line.timings))
            println(
                "3D surface vs 2.4 tiles: ${reads.mismatches} mismatches in ${reads.compared} chunks"
            )
        }
    }
}
