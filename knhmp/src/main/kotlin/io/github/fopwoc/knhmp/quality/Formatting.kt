package io.github.fopwoc.knhmp.quality

import com.diffplug.gradle.spotless.FormatExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Project
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.api.provider.Provider

private const val SPOTLESS_PLUGIN_ID = "com.diffplug.spotless"

/** Spotless over [sources]; `spotlessCheck` joins `check`, `spotlessApply` reformats. */
internal fun Project.applyFormatting(formatting: KnhMpFormatting, sources: QualitySources) {
    applyDeclaredPlugin(SPOTLESS_PLUGIN_ID, "spotless")
    val spotless = extensions.getByType(SpotlessExtension::class.java)
    formatting.ktfmtVersion?.let(::versionOf)?.let { version ->
        spotless.kotlin { kotlin ->
            kotlin.target(sources.files(this, "**/*.kt"))
            kotlin.ktfmt(version).kotlinlangStyle()
            kotlin.keepStonecutterComments()
        }
        spotless.kotlinGradle { scripts ->
            scripts.target(files(sources.gradleScripts))
            scripts.ktfmt(version).kotlinlangStyle()
        }
    }
    formatting.palantirJavaFormatVersion?.let(::versionOf)?.let { version ->
        spotless.java { java ->
            java.target(sources.files(this, "**/*.java"))
            java.palantirJavaFormat(version)
            java.keepStonecutterComments()
        }
    }
}

/**
 * Stonecutter only reads its comments as `//?`; formatters write every line comment as `// ?`,
 * which Stonecutter silently ignores. This step, after the formatter, restores the marker.
 * Stonecutter accepts the rest of the formatter's layout as is.
 */
private fun FormatExtension.keepStonecutterComments() =
    replaceRegex("Stonecutter comments", """(?m)^([ \t]*)// \?""", "$1//?")

internal fun versionOf(version: Any): String =
    if (version is Provider<*>) version.get().toString() else version.toString()

/** Applies a plugin the root build declared with `apply false`, or explains how to declare it. */
internal fun Project.applyDeclaredPlugin(id: String, catalogAlias: String) {
    try {
        pluginManager.apply(id)
    } catch (failure: UnknownPluginException) {
        throw IllegalStateException(
            "knhmpQuality needs $id on the root build's classpath: plugins { alias(libs.plugins.$catalogAlias) apply false }",
            failure,
        )
    }
}
