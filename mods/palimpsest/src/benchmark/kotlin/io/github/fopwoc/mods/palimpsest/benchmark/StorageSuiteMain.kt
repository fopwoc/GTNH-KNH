package io.github.fopwoc.mods.palimpsest.benchmark

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Headless entry point for `./gradlew storageSuite`; writes the report under the given directory.
 */
fun main(args: Array<String>) {
    val directory = Path.of(args.singleOrNull() ?: "build/palimpsest")
    val result =
        BenchmarkStorageSuite.run(
            directory,
            wideWorldSide = 512,
            giantWorldSide = 1024,
            onProgress = ::println,
        )
    println()
    println(Files.readString(result.file))
    println("report=${result.file.toAbsolutePath()}")
    if (result.status != BenchmarkStorageSuite.Status.PASS) exitProcess(1)
}
