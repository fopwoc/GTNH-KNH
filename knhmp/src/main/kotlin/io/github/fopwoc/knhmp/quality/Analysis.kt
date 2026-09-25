package io.github.fopwoc.knhmp.quality

import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import dev.detekt.gradle.report.ReportMergeTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

private const val DETEKT_PLUGIN_ID = "dev.detekt"
private const val BASELINE_FILE = "detekt-baseline.xml"
private const val MERGE_TASK = "detektReport"

/**
 * Plain detekt over [sources], on top of detekt's default rules; `detekt` joins `check`. Only
 * findings of `error` severity fail it, so a config can make style rules `warning`s. Every report
 * also lands in the root's merged SARIF, `build/reports/detekt/merged.sarif`, for CI. The
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
        failOnSeverity.set(FailOnSeverity.Error)
        projectDir.resolve(BASELINE_FILE).takeIf { it.isFile }?.let { baseline.set(it) }
    }
    analysis.ruleSets.forEach { dependencies.add("detektPlugins", it) }

    val merge = rootProject.detektReportMerge()
    merge.configure { it.input.from(layout.buildDirectory.file("reports/detekt/detekt.sarif")) }
    tasks.named("detekt").configure { it.finalizedBy(merge) }
}

/** The root task merging every project's SARIF report; it runs even when detekt fails. */
private fun Project.detektReportMerge(): TaskProvider<ReportMergeTask> =
    if (MERGE_TASK in tasks.names) tasks.named(MERGE_TASK, ReportMergeTask::class.java)
    else
        tasks.register(MERGE_TASK, ReportMergeTask::class.java) { merge ->
            merge.description = "Merges the detekt SARIF reports of every project."
            merge.output.set(layout.buildDirectory.file("reports/detekt/merged.sarif"))
        }
