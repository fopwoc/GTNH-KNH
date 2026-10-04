plugins {
    id("io.github.fopwoc.knhmp.library")
}

// The oldest runtime Palimpsest ships for: Java 21 (Minecraft 1.21.1) and Forgelin's Kotlin stdlib
// on GTNH. Every loader's island compiles these sources again with its own toolchain.
knhmpLibrary {
    jvmTarget = 21
    stdlibVersion = libs.versions.gtnhKotlinStdlib.get()
}
