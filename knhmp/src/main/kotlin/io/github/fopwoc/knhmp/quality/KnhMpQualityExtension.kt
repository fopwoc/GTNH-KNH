package io.github.fopwoc.knhmp.quality

import org.gradle.api.Action

/**
 * Formatting and analysis for the whole repository: every KnhMP module, the root build scripts and
 * [extraSources]. Each part is optional; a repository that declares neither gets nothing applied.
 */
open class KnhMpQualityExtension {
    internal var formatting: KnhMpFormatting? = null
        private set

    internal var analysis: KnhMpAnalysis? = null
        private set

    internal val extraSources = mutableListOf<Any>()

    fun formatting(action: Action<in KnhMpFormatting>) =
        action.execute(formatting ?: KnhMpFormatting().also { formatting = it })

    fun analysis(action: Action<in KnhMpAnalysis>) =
        action.execute(analysis ?: KnhMpAnalysis().also { analysis = it })

    /**
     * Directories outside KnhMP modules that are held to the same rules, e.g. an included build.
     */
    fun extraSources(vararg directories: Any) {
        extraSources.addAll(directories)
    }
}
