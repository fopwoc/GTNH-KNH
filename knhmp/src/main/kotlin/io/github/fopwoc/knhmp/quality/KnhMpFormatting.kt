package io.github.fopwoc.knhmp.quality

/**
 * Formatting through Spotless. Tool versions are given by the build (`libs.versions.ktfmt`), so a
 * formatter update never needs a KnhMP release.
 */
class KnhMpFormatting {
    internal var ktfmtVersion: Any? = null
        private set

    internal var palantirJavaFormatVersion: Any? = null
        private set

    /**
     * Kotlin sources and Gradle Kotlin scripts, in ktfmt's Kotlin-coding-conventions style (4
     * spaces).
     */
    fun ktfmt(version: Any) {
        ktfmtVersion = version
    }

    /** Java sources: mixins, `legacyJava`. */
    fun palantirJavaFormat(version: Any) {
        palantirJavaFormatVersion = version
    }
}
