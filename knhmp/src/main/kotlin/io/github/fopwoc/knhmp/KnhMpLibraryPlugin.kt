package io.github.fopwoc.knhmp

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

/**
 * `io.github.fopwoc.knhmp.library`: a Kotlin/JVM project with `src/main`, `src/test` and JUnit 5,
 * pinned to [KnhMpLibraryExtension]'s baseline. Mods consume its sources, not its jar, so the
 * settings are also published to the consumers as plain values (see [KnhMpLibraryBaseline]).
 */
class KnhMpLibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) =
        with(project) {
            pluginManager.apply(KOTLIN_JVM_PLUGIN)
            val extension = extensions.create("knhmpLibrary", KnhMpLibraryExtension::class.java)
            dependencies.add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5")
            tasks.withType(Test::class.java).configureEach { it.useJUnitPlatform() }
            afterEvaluate {
                val kotlin = extensions.getByType(KotlinJvmProjectExtension::class.java)
                kotlin.jvmToolchain(extension.javaToolchain)
                extension.stdlibVersion?.let { kotlin.coreLibrariesVersion = it }
                kotlin.compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(extension.jvmTarget.asKotlinJvmTarget()))
                    extension.apiVersion?.let { level ->
                        apiVersion.set(KotlinVersion.fromVersion(level))
                        languageVersion.set(KotlinVersion.fromVersion(level))
                    }
                }
                tasks.withType(JavaCompile::class.java).configureEach {
                    it.options.release.set(extension.jvmTarget)
                }
                KnhMpLibraryBaseline(extension.jvmTarget, extension.apiVersion).publish(this)
            }
        }

    private companion object {
        const val KOTLIN_JVM_PLUGIN = "org.jetbrains.kotlin.jvm"
    }
}
