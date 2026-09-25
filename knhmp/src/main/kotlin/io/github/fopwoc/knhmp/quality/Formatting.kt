package io.github.fopwoc.knhmp.quality

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
        }
    }
}

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
