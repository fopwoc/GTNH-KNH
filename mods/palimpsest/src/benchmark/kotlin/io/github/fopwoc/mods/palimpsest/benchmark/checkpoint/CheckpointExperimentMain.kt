package io.github.fopwoc.mods.palimpsest.benchmark.checkpoint

import io.github.fopwoc.mods.palimpsest.benchmark.BenchmarkStorageSuite
import java.nio.file.Files
import java.nio.file.Path

/** Full storage suite plus sparse and dense refreshes against the tag's public storage API. */
fun main(args: Array<String>) {
    val directory = Path.of(args.single())
    Files.createDirectories(directory)
    Files.newBufferedWriter(directory.resolve("refresh.txt")).use { writer ->
        CheckpointRefreshTrial.run(directory.resolve("refresh-work")) {
            writer.appendLine(it)
            writer.flush()
            println(it)
        }
    }
    val result =
        BenchmarkStorageSuite.run(
            directory,
            wideWorldSide = 512,
            giantWorldSide = 1024,
            onProgress = ::println,
        )
    println(Files.readString(result.file))
    check(result.status == BenchmarkStorageSuite.Status.PASS)
}
