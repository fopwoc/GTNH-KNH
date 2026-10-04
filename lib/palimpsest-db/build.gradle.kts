plugins {
    id("io.github.fopwoc.knhmp.library")
}

// The oldest runtime Palimpsest ships for: Java 21 (Minecraft 1.21.1) and Forgelin's Kotlin stdlib
// on GTNH. Every loader's island compiles these sources again with its own toolchain.
knhmpLibrary {
    jvmTarget = 21
    stdlibVersion = libs.versions.gtnhKotlinStdlib.get()
}

// Synthetic workloads against the public API only; results go to the console and stay out of git.
val benchmark by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Runs the synthetic storage benchmark; pass scenario names with --args."
    classpath = benchmark.runtimeClasspath
    mainClass.set("io.github.fopwoc.palimpsest.db.benchmark.BenchmarkMainKt")
    maxHeapSize = "8g"
}
