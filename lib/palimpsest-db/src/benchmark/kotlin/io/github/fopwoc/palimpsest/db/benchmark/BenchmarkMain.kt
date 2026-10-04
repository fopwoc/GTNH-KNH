package io.github.fopwoc.palimpsest.db.benchmark

import kotlin.io.path.createTempDirectory

/**
 * `./gradlew :palimpsest-db:benchmark
 * [--args="explore build | scaling | real <save> [dimension]"]`: every synthetic scenario by
 * default, `scaling` for commit throughput against the background pool size, `real` for one
 * dimension of a 1.7.10 save (`region` or `DIM-1/region`).
 */
fun main(args: Array<String>) {
    val root = createTempDirectory("palimpsest-bench-")
    val cores = Runtime.getRuntime().availableProcessors()
    try {
        if (args.firstOrNull() == "real")
            RealWorldRun(
                    java.nio.file.Path.of(args[1]),
                    args.getOrElse(2) { "region" },
                    root,
                    cores - 2,
                )
                .run()
        else if (args.contains("scaling")) scaling(root, cores)
        else {
            val kinds = Scenario.ALL.filter { args.isEmpty() || it.name in args }
            println(
                "${kinds.size} scenarios, $cores cores, background pool ${cores - 2}, read pool ${cores - 2}\n"
            )
            for (kind in kinds) report(kind, Runner(root, cores - 2, cores - 2).run(kind))
        }
    } finally {
        root.toFile().deleteRecursively()
    }
}

private fun report(kind: Scenario.Kind, result: Runner.Result) {
    val label = if (kind.realistic) "realistic" else "worst case"
    println("== ${kind.name} ($label): ${kind.summary}")
    println(
        "   ${result.commits} commits, ${result.observed} chunks observed, ${result.versions} chunk versions, " +
            "${result.sectionsWritten} sections written"
    )
    println(
        "   disk ${bytes(result.disk.toDouble())}, " +
            "${bytes(result.disk.toDouble() / result.versions.coerceAtLeast(1))} per chunk version, " +
            "${bytes(result.disk.toDouble() / result.commits)} per commit"
    )
    println(
        "   commit mean ${millis(result.commit.mean())}, p95 ${millis(result.commit.percentile(0.95).toDouble())}, " +
            "max ${millis(result.commit.percentile(1.0).toDouble())}; " +
            "${micros(result.commit.total.toDouble() / result.observed)} per observed chunk"
    )
    println("   close ${millis(result.close.toDouble())}, reopen ${millis(result.open.toDouble())}")
    println(
        "   read chunk (${result.probes} probes): first ${micros(result.firstRead.mean())}, " +
            "warm ${micros(result.warmRead.mean())}, mid-history ${micros(result.pastRead.mean())}, " +
            "parallel batch ${micros(result.parallelRead.toDouble())} per chunk\n"
    )
}

private fun scaling(root: java.nio.file.Path, cores: Int) {
    val explore = Scenario.ALL.first { it.name == "explore" }
    println("explore: commit time against background threads\n")
    for (threads in listOf(1, 2, 4, 8, cores - 2, cores).distinct()) {
        val result = Runner(root, threads, cores - 2).run(explore)
        println(
            "   %2d threads: mean %s per commit, %s per observed chunk"
                .format(
                    threads,
                    millis(result.commit.mean()),
                    micros(result.commit.total.toDouble() / result.observed),
                )
        )
    }
}
