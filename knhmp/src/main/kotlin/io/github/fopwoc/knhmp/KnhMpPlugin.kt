package io.github.fopwoc.knhmp

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin

class KnhMpPlugin : Plugin<Project> {

    override fun apply(project: Project) = with(project) {
        pluginManager.apply(BasePlugin::class.java)
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")

        // Other modules consume this module's outputs (facade jars, collected island jars), so a
        // `clean` in the same invocation must never run after them.
        tasks.configureEach { task ->
            if (!task.name.startsWith(BasePlugin.CLEAN_TASK_NAME)) task.mustRunAfter(BasePlugin.CLEAN_TASK_NAME)
        }

        val extension = extensions.create("knhmp", KnhMpExtension::class.java, project)
        afterEvaluate {
            check(extension.targets.all().isNotEmpty()) { "KnhMP module declares no targets" }
            applyDefaultSourceGraph(extension)
            validateTargetSourceSets(extension)
            verifyPackageNames(extension)
            KnhMpModMetadata.generate(this, extension)
            val islands = createIslands(extension)
            configureIslands(extension, islands)
            configureIdeProjection(extension, islands)
            registerJarVerification(extension, islands)
            registerMixinVerification(extension, islands)
        }
    }

    private fun applyDefaultSourceGraph(extension: KnhMpExtension) {
        if (!extension.sourceSets.isEmpty) return

        val commonMain = extension.sourceSets.sourceSet("commonMain")
        extension.targets.all().forEach { target ->
            extension.sourceSets.sourceSet(target.sourceSet).dependsOn(commonMain)
        }
    }

    private fun validateTargetSourceSets(extension: KnhMpExtension) {
        extension.targets.all().forEach { target ->
            target.sourceSets().forEach { leaf ->
                check(leaf in extension.sourceSets.names()) { "Target ${target.name} refers to unknown source set $leaf" }
            }
        }
    }
}
