package io.github.fopwoc.knhmp

import java.io.File
import org.gradle.api.Project

/**
 * An island with one leaf source set and a Stonecutter tree. Stonecutter reads every source root of
 * the closure through symlinks under the island's `src/`, so versioned comments work in parents and
 * tests as well as in the leaf, and switching the active version rewrites the canonical files in
 * place.
 *
 * Several islands of a module can share canonical files (a leaf compiled by two Loom generations,
 * parents such as `commonMain`), so every island carries the module's whole Stonecutter version
 * list, [treeVersions], and all of them share one active version. Versions the island does not
 * build are stub nodes. Backends supply the node build script.
 */
internal abstract class KnhMpStonecutterIsland(
    module: Project,
    extension: KnhMpExtension,
    target: KnhMpTarget,
    name: String,
    final override val nodes: List<KnhMpIslandNode>,
    /** Every Stonecutter version of the module, oldest first; a superset of [nodes]. */
    val treeVersions: List<String>,
) : KnhMpIsland(module, extension, target, name) {

    init {
        check(
            nodes.isNotEmpty() &&
                nodes.all { it.minecraftVersion != null && it.sourceSet == nodes.first().sourceSet }
        )
        check(treeVersions.containsAll(nodes.map { it.minecraftVersion })) {
            "Stonecutter tree ${treeVersions.joinToString()} misses a node of $name"
        }
    }

    val versions: List<String> = nodes.map { checkNotNull(it.minecraftVersion) }

    private val controller: File
        get() = directory.resolve("stonecutter.gradle.kts")

    fun activeVersion(): String {
        val active =
            ACTIVE_VERSION.find(controller.readText())?.groupValues?.get(1)
                ?: error("Cannot read the active Stonecutter version from $controller")
        check(active in treeVersions) {
            "Active Stonecutter version $active is not one of ${treeVersions.joinToString()} in $directory"
        }
        return active
    }

    /** The active version's node, or the newest node when this island does not build it. */
    override fun activeNode(): KnhMpIslandNode =
        nodes.firstOrNull { it.minecraftVersion == activeVersion() } ?: nodes.last()

    override fun nodeBuildDirectory(node: KnhMpIslandNode): File =
        directory.resolve("versions/${node.minecraftVersion}/build")

    override fun generate() {
        directory.mkdirs()
        writeGenerated("settings.gradle.kts", settingsScript())
        if (!controller.isFile) controller.writeText(controllerScript())
        nodes.forEach { node ->
            writeGenerated("build-${node.minecraftVersion}.gradle.kts", nodeScript(node))
        }
        if (treeVersions.size > versions.size) writeGenerated(STUB_SCRIPT, STUB)
        directory.resolve("build.gradle.kts").delete()
        ensureSymbolicLink(
            directory.resolve("src/main/kotlin"),
            module.projectDir.resolve("src/$sourceSet/kotlin"),
        )
        ensureSymbolicLink(directory.resolve("src/main/java"), module.javaSourceRoot(sourceSet))
        ensureSymbolicLink(
            directory.resolve("src/main/resources"),
            module.projectDir.resolve("src/$sourceSet/resources"),
        )
        versionedRoots(nodes.first()).forEach { root ->
            ensureSymbolicLink(directory.resolve("src/${root.path}"), root.canonical)
        }
    }

    /**
     * A canonical source root Stonecutter processes through the island link `src/<set>/<name>`:
     * every parent's Kotlin and Java roots under `main`, every test root of the closure under
     * `test`.
     */
    private class VersionedRoot(val set: String, val name: String, val canonical: File) {
        val path: String = "$set/$name"
        val kind: String = name.substringAfterLast('-')
    }

    private fun versionedRoots(node: KnhMpIslandNode): List<VersionedRoot> {
        fun roots(set: String, sourceSet: String) =
            listOf(
                    VersionedRoot(
                        set,
                        "$sourceSet-kotlin",
                        module.projectDir.resolve("src/$sourceSet/kotlin"),
                    ),
                    VersionedRoot(set, "$sourceSet-java", module.javaSourceRoot(sourceSet)),
                )
                .filter { it.canonical.isDirectory }
        val closure = closure(node)
        return closure.filter { it != node.sourceSet }.flatMap { roots("main", it) } +
            closure.flatMap { roots("test", testSourceSetOf(it)) }
    }

    /**
     * Mounts the closure's parents and tests for one node. The leaf's own `src/main` roots are
     * wired by Stonecutter itself; the other linked roots are processed by Stonecutter too but
     * mounted here: the active version compiles the canonical files, every other version
     * Stonecutter's processed copies. Resources and generated sources are not versioned and mount
     * directly.
     */
    protected fun versionedMountScript(node: KnhMpIslandNode): String {
        val roots = versionedRoots(node)
        fun mounts(set: String, kind: String) =
            roots
                .filter { it.set == set && it.kind == kind }
                .map { "knhmpVersioned(\"${it.set}\", \"${it.name}\")" }
        val parents = closure(node).filter { it != node.sourceSet }
        val generated = parents.flatMap { parent ->
            module.kotlinSourceRoots(extension, parent) -
                module.projectDir.resolve("src/$parent/kotlin")
        }
        val parentResources = parents.map { module.projectDir.resolve("src/$it/resources") }
        val testResources =
            closure(node).map {
                module.projectDir.resolve("src/${testSourceSetOf(it)}/resources")
            }
        fun files(dirs: List<File>) = dirs.map { "file(\"${it.path.escape()}\")" }
        return """
            // Stonecutter processes every root linked under src/; the active version compiles the
            // canonical files, the others Stonecutter's processed copies.
            fun knhmpVersioned(set: String, root: String): Any =
                if (stonecutter.current.isActive) rootDir.resolve("src/${'$'}set/${'$'}root")
                else files(layout.buildDirectory.dir("generated/stonecutter/${'$'}set/${'$'}root"))
                    .builtBy(if (set == "main") "stonecutterGenerate" else "stonecutterGenerateTest")

            kotlin {
                sourceSets.named("main") {
            ${(mounts("main", "kotlin") + files(generated)).map { "kotlin.srcDir($it)" }.block(8, 12)}
            ${files(parentResources).map { "resources.srcDir($it)" }.block(8, 12)}
                }
                sourceSets.named("test") {
                    kotlin.setSrcDirs(listOf<Any>(${mounts("test", "kotlin").joinToString()}))
                    resources.setSrcDirs(listOf<Any>(${files(testResources).joinToString()}))
                }
            }
            the<org.gradle.api.tasks.SourceSetContainer>().named("main") {
            ${mounts("main", "java").map { "java.srcDir($it)" }.block(4, 12)}
            }
            the<org.gradle.api.tasks.SourceSetContainer>().named("test") {
                java.setSrcDirs(listOf<Any>(${mounts("test", "java").joinToString()}))
            }
        """
            .trimIndent()
    }

    /** Extra plugin repositories the backend's build plugins are published to. */
    protected abstract val pluginRepositories: List<String>

    protected abstract fun nodeScript(node: KnhMpIslandNode): String

    /**
     * Stonecutter's tasks and comments span the whole tree; `check(project.name == version)` guards
     * each node script.
     */
    protected fun nodeGuard(node: KnhMpIslandNode): String =
        "check(project.name == \"${node.minecraftVersion}\") { \"${target.name} build script used by unexpected version: \${'$'}name\" }"

    private fun settingsScript(): String {
        val stonecutterVersion =
            nodes.first().configuration.plugin(STONECUTTER_PLUGIN)?.version ?: STONECUTTER_VERSION
        return """
        $HEADER
        ${settingsPluginManagement(*pluginRepositories.toTypedArray()).indent(8)}

        plugins {
            id("$STONECUTTER_PLUGIN") version "$stonecutterVersion"
            id("org.gradle.toolchains.foojay-resolver-convention") version "$FOOJAY_VERSION"
        }

        rootProject.name = "$buildName"

        // The module's whole version tree; versions another island builds are stub nodes.
        stonecutter {
            create(rootProject) {
        ${treeVersions.map { "version(\"$it\").buildscript(\"${if (it in versions) "build-$it.gradle.kts" else STUB_SCRIPT}\")" }.block(8, 8)}
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
        """
            .trimIndent() + "\n"
    }

    private fun controllerScript(): String =
        """
        plugins {
            id("$STONECUTTER_PLUGIN")
        }

        stonecutter active "${treeVersions.last()}"
    """
            .trimIndent() + "\n"

    companion object {
        const val STONECUTTER_PLUGIN = "dev.kikugie.stonecutter"
        private const val STONECUTTER_VERSION = "0.9.8"
        private const val STUB_SCRIPT = "stub.gradle.kts"
        private const val STUB =
            "// A version another island of this module builds; present so every island shares one\n" +
                "// Stonecutter version list and active version.\n"
        private val ACTIVE_VERSION = Regex("""stonecutter\s+active\s+[\"']([^\"']+)[\"']""")

        /** Oldest first, comparing the numeric parts: `1.21.1` < `1.21.11` < `26.2`. */
        val VERSION_ORDER: Comparator<String> = Comparator { a, b ->
            val left = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val right = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
            left.zip(right).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 }
                ?: left.size.compareTo(right.size)
        }

        /** Minecraft 26.1 is the first unobfuscated release. */
        fun isObfuscated(minecraftVersion: String): Boolean = minecraftVersion.startsWith("1.")
    }
}
