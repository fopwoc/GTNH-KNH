package io.github.fopwoc.knhmp.quality

/**
 * Static analysis through detekt, on source files alone (no type resolution). Each project may keep
 * a `detekt-baseline.xml` of accepted findings next to its build script.
 */
class KnhMpAnalysis {
    internal val configFiles = mutableListOf<Any>()
    internal val ruleSets = mutableListOf<Any>()

    /**
     * Detekt configuration on top of detekt's defaults, e.g.
     * `rootProject.file("config/detekt.yml")`.
     */
    fun config(file: Any) {
        configFiles += file
    }

    /** A detekt rule set, e.g. `libs.compose.rules.detekt`. */
    fun ruleSet(notation: Any) {
        ruleSets += notation
    }
}
