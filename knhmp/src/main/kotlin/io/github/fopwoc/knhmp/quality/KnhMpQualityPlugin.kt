package io.github.fopwoc.knhmp.quality

import java.io.File
import org.gradle.api.file.FileTree
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applied once, to the root project: configures formatting and analysis in every project that
 * applies KnhMP, and in the root project for its build scripts and the declared extra sources.
 * Spotless and detekt must be on the root build's plugin classpath (`apply false`), which is where
 * their versions come from.
 */
class KnhMpQualityPlugin : Plugin<Project> {
    override fun apply(root: Project) {
        check(root == root.rootProject) { "io.github.fopwoc.knhmp.quality belongs on the root project, not ${root.path}" }
        val extension = root.extensions.create("knhmpQuality", KnhMpQualityExtension::class.java)

        root.subprojects { project ->
            project.plugins.withId(KNHMP_PLUGIN_ID) {
                project.afterEvaluate { project.applyQuality(extension, ModuleSources(project)) }
            }
        }
        root.afterEvaluate { root.applyQuality(extension, RootSources(root, extension)) }
    }

    private fun Project.applyQuality(extension: KnhMpQualityExtension, sources: QualitySources) {
        extension.formatting?.let { applyFormatting(it, sources) }
        extension.analysis?.let { applyAnalysis(it, sources) }
    }

    private companion object {
        const val KNHMP_PLUGIN_ID = "io.github.fopwoc.knhmp"
    }
}

/** What one project formats and analyses: the source directories it owns, and its build scripts. */
internal interface QualitySources {
    /** Directories holding sources; walked for `.kt` and `.java`, skipping build output. */
    val roots: List<File>

    /** Build scripts outside [roots]. */
    val gradleScripts: List<File>

    fun files(project: Project, pattern: String): FileTree =
        roots.filter(File::isDirectory)
            .map { root -> project.fileTree(root) { it.include(pattern).exclude(EXCLUDED) } }
            .fold(project.files().asFileTree, FileTree::plus)

    companion object {
        val EXCLUDED = listOf("**/build/**", "**/.gradle/**", "**/.knhmp/**")
    }
}

/** A KnhMP module: everything under `src/`, and its build script. */
internal class ModuleSources(project: Project) : QualitySources {
    override val roots = listOf(project.file("src"))
    override val gradleScripts = project.projectDir.listFiles { file -> file.name.endsWith(".gradle.kts") }.orEmpty().toList()
}

/** The root project: its own build scripts, and whole extra directories. */
internal class RootSources(root: Project, extension: KnhMpQualityExtension) : QualitySources {
    override val roots = extension.extraSources.map(root::file)
    override val gradleScripts = root.projectDir.listFiles { file -> file.name.endsWith(".gradle.kts") }.orEmpty().toList() +
        roots.flatMap { dir -> dir.parentFile.listFiles { file -> file.name.endsWith(".gradle.kts") }.orEmpty().toList() }.distinct()
}
