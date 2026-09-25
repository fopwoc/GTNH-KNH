package io.github.fopwoc.knhmp

import org.gradle.api.Action
import org.gradle.api.Project

open class KnhMpExtension(private val project: Project) {

    val targets = KnhMpTargets()
    val sourceSets = KnhMpSourceSets()
    val common = KnhMpBuildScope()
    val repositories = KnhMpRepositories()

    var modId = stringProperty("modId", project.name.replace("-", ""))
    var modName = stringProperty("modName", modId)
    var modGroup = stringProperty("modGroup", "io.github.example.$modId")
    var modVersion: String
        get() = explicitModVersion ?: KnhMpBuildIdentity.version(project.rootDir)
        set(value) {
            explicitModVersion = value
        }
    private var explicitModVersion: String? = project.findProperty("modVersion")?.toString()

    /** Web page of the source repository: the `repositoryUrl` property, else derived from the `origin` remote. */
    var repositoryUrl: String
        get() = explicitRepositoryUrl ?: KnhMpBuildIdentity.repositoryUrl(project.rootDir)
        set(value) {
            explicitRepositoryUrl = value
        }
    private var explicitRepositoryUrl: String? = project.findProperty("repositoryUrl")?.toString()

    /** Jar base name, e.g. `knh-core` produces `knh-core-gtnh-<version>.jar`. */
    var archiveName: String = project.name
    var javaToolchain = 26
    internal val jvmTargetExceptions = mutableMapOf<String, Int>()

    /** A generated class intentionally compiled for an older JVM than the target's main sources. */
    fun jvmTargetException(className: String, target: Int) {
        require(target >= 8) { "JVM target must be at least 8" }
        jvmTargetExceptions[className.replace('.', '/') + ".class"] = target
    }

    fun targets(action: Action<in KnhMpTargets>) = action.execute(targets)

    /** Repositories every compiler island of this module and of its dependents resolves from. */
    fun repositories(action: Action<in KnhMpRepositories>) = action.execute(repositories)

    fun sourceSets(action: Action<in KnhMpSourceSets>) = action.execute(sourceSets)

    /** Build plugins applied to every target and variant of this module. */
    fun plugins(action: Action<in KnhMpPlugins>) = action.execute(common.plugins)

    /** Dependencies of every target and variant of this module, e.g. `module(":framework")`. */
    fun dependencies(action: Action<in KnhMpDependencies>) = action.execute(common.dependencies)

    private fun stringProperty(name: String, fallback: String): String =
        project.findProperty(name)?.toString() ?: fallback
}
