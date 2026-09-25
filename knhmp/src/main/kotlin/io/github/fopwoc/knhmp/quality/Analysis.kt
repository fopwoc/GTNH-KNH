package io.github.fopwoc.knhmp.quality

import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project

private const val DETEKT_PLUGIN_ID = "dev.detekt"
private const val BASELINE_FILE = "detekt-baseline.xml"

/**
 * Plain detekt over [sources], on top of detekt's default rules; `detekt` joins `check`. The
 * per-compilation tasks detekt adds to Kotlin Multiplatform projects would type-resolve against the
 * IDE facade and stay unused.
 */
internal fun Project.applyAnalysis(analysis: KnhMpAnalysis, sources: QualitySources) {
    val kotlin = sources.files(this, "**/*.kt")
    applyDeclaredPlugin(DETEKT_PLUGIN_ID, "detekt")
    extensions.getByType(DetektExtension::class.java).apply {
        source.setFrom(kotlin)
        config.setFrom(analysis.configFiles)
        buildUponDefaultConfig.set(true)
        parallel.set(true)
        projectDir.resolve(BASELINE_FILE).takeIf { it.isFile }?.let { baseline.set(it) }
    }
    analysis.ruleSets.forEach { dependencies.add("detektPlugins", it) }
}
