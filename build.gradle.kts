plugins {
    base
    // One plugin classloader for every module: KnhMP resolves cross-module dependencies in memory.
    id("io.github.fopwoc.knhmp") apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
