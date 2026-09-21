package io.github.fopwoc.knhmp

import java.io.File
import org.gradle.api.Project

/**
 * An island with one leaf source set and a Stonecutter tree holding one node per Minecraft
 * version. Stonecutter reads the canonical leaf source set through `src/main` symlinks, so
 * switching the active version rewrites the canonical files in place. Backends supply the node
 * build script.
 */
internal abstract class KnhMpStonecutterIsland(
    module: Project,
    extension: KnhMpExtension,
    target: KnhMpTarget,
    name: String,
    final override val nodes: List<KnhMpIslandNode>,
) : KnhMpIsland(module, extension, target, name) {

    init {
        check(nodes.isNotEmpty() && nodes.all { it.minecraftVersion != null && it.sourceSet == nodes.first().sourceSet })
    }

    val versions: List<String> = nodes.map { checkNotNull(it.minecraftVersion) }

    private val controller: File get() = directory.resolve("stonecutter.gradle.kts")

    fun activeVersion(): String {
        val active = ACTIVE_VERSION.find(controller.readText())?.groupValues?.get(1)
            ?: error("Cannot read the active Stonecutter version from $controller")
        check(active in versions) { "Active Stonecutter version $active is not one of ${versions.joinToString()} in $directory" }
        return active
    }

    override fun activeNode(): KnhMpIslandNode = nodes.first { it.minecraftVersion == activeVersion() }

    override fun nodeBuildDirectory(node: KnhMpIslandNode): File = directory.resolve("versions/${node.minecraftVersion}/build")

    override fun generate() {
        directory.mkdirs()
        writeGenerated("settings.gradle.kts", settingsScript())
        if (!controller.isFile) controller.writeText(controllerScript())
        nodes.forEach { node -> writeGenerated("build-${node.minecraftVersion}.gradle.kts", nodeScript(node)) }
        directory.resolve("build.gradle.kts").delete()
        ensureSymbolicLink(directory.resolve("src/main/kotlin"), module.projectDir.resolve("src/$sourceSet/kotlin"))
        ensureSymbolicLink(directory.resolve("src/main/java"), module.javaSourceRoot(sourceSet))
        ensureSymbolicLink(directory.resolve("src/main/resources"), module.projectDir.resolve("src/$sourceSet/resources"))
    }

    /** Extra plugin repositories the backend's build plugins are published to. */
    protected abstract val pluginRepositories: List<String>

    protected abstract fun nodeScript(node: KnhMpIslandNode): String

    /** Stonecutter's tasks and comments span the whole tree; `check(project.name == version)` guards each node script. */
    protected fun nodeGuard(node: KnhMpIslandNode): String =
        "check(project.name == \"${node.minecraftVersion}\") { \"${target.name} build script used by unexpected version: \${'$'}name\" }"

    private fun settingsScript(): String {
        val stonecutterVersion = nodes.first().configuration.plugin(STONECUTTER_PLUGIN)?.version ?: STONECUTTER_VERSION
        return """
        $HEADER
        ${settingsPluginManagement(*pluginRepositories.toTypedArray()).indent(8)}

        plugins {
            id("$STONECUTTER_PLUGIN") version "$stonecutterVersion"
            id("org.gradle.toolchains.foojay-resolver-convention") version "$FOOJAY_VERSION"
        }

        rootProject.name = "$buildName"

        stonecutter {
            create(rootProject) {
        ${versions.map { "version(\"$it\").buildscript(\"build-$it.gradle.kts\")" }.block(8, 8)}
            }
        }

        // This build only ever runs as a nested GradleBuild from the module; IntelliJ never imports it.
        // The daemon-wide idea.sync.active property still leaks in during the module's IDE sync, and
        // Stonecutter then injects task requests whose rootDir is a project directory (versions/<v>),
        // which Gradle accepts only for the build-tree root or an included build. Drop them; the
        // module build owns all IDE behaviour.
        gradle.projectsEvaluated {
            startParameter.setTaskRequests(startParameter.taskRequests.filter { it.rootDir == null })
        }
        """.trimIndent() + "\n"
    }

    private fun controllerScript(): String = """
        plugins {
            id("$STONECUTTER_PLUGIN")
        }

        stonecutter active "${versions.last()}"
    """.trimIndent() + "\n"

    companion object {
        const val STONECUTTER_PLUGIN = "dev.kikugie.stonecutter"
        private const val STONECUTTER_VERSION = "0.9.8"
        private val ACTIVE_VERSION = Regex("""stonecutter\s+active\s+[\"']([^\"']+)[\"']""")

        /** Minecraft 26.1 is the first unobfuscated release. */
        fun isObfuscated(minecraftVersion: String): Boolean = minecraftVersion.startsWith("1.")
    }
}
