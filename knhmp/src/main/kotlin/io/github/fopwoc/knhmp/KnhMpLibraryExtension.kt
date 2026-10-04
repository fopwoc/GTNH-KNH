package io.github.fopwoc.knhmp

/**
 * A pure JVM library that KnhMP mods compile into themselves (`include(...)` on a source set). It
 * builds and tests on its own against the oldest runtime of the mods that include it, so code that
 * would fail on one loader fails here first.
 */
open class KnhMpLibraryExtension {
    /** Bytecode level; no including island may target an older one. */
    var jvmTarget: Int = 21
        set(value) {
            require(value >= 8) { "jvmTarget must be at least 8: $value" }
            field = value
        }

    /**
     * Kotlin stdlib of the oldest loader adapter among the including mods, e.g. Forgelin's
     * `"2.1.10"`. The library compiles against it, at its API and language level.
     */
    var stdlibVersion: String? = null

    /** JDK that compiles and tests the library. */
    var javaToolchain: Int = 26

    /** `major.minor` of [stdlibVersion]; null when the library is not pinned. */
    internal val apiVersion: String?
        get() = stdlibVersion?.split('.')?.take(2)?.joinToString(".")
}
