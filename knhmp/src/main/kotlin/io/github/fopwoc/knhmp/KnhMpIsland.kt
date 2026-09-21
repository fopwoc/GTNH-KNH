package io.github.fopwoc.knhmp

import java.io.File
import java.nio.file.Files
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

/** One compiler project inside an island: the island root (`:`) or a Stonecutter node (`:26.1`). */
internal class KnhMpIslandNode(
    val minecraftVersion: String?,
    val projectPath: String,
    val sourceSet: String,
    val configuration: KnhMpEffectiveConfiguration,
) {
    fun task(name: String): String = if (projectPath == ":") ":$name" else "$projectPath:$name"
}

/**
 * An independent Gradle build under `modules/<module>/.knhmp/<name>` that compiles one leaf source
 * set of one target in one build-tool environment (build plugins, Loom/ModDev generation, KGP).
 * The module build generates it from the evaluated DSL and drives it through nested [GradleBuild]
 * tasks; IntelliJ never imports it. Islands own their settings, plugin classpath and repositories.
 */
internal abstract class KnhMpIsland(
    val module: Project,
    val extension: KnhMpExtension,
    val target: KnhMpTarget,
    /** Directory name under `.knhmp`, e.g. `fabric-legacy`. */
    val name: String,
) {
    val directory: File = module.projectDir.resolve(".knhmp/$name")
    val title: String = name.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }
    val buildName: String = "${module.name}-$name"
    val ideClasspathTask: String = "resolve${title}IdeClasspath"

    abstract val nodes: List<KnhMpIslandNode>
    val sourceSet: String get() = nodes.first().sourceSet

    abstract fun generate()

    /** The node whose environment the IDE facade and unversioned run tasks follow. */
    open fun activeNode(): KnhMpIslandNode = nodes.single()

    open fun nodeBuildDirectory(node: KnhMpIslandNode): File = directory.resolve("build")

    fun jar(node: KnhMpIslandNode): File {
        val version = node.minecraftVersion?.let { "-$it" }.orEmpty()
        return nodeBuildDirectory(node).resolve("libs/${module.name}-${target.name}$version-${extension.modVersion}.jar")
    }

    /** Jar other modules compile against in dev: deobfuscated where the backend remaps for shipping. */
    open fun devJar(node: KnhMpIslandNode): File = jar(node)

    /** The module task that builds exactly [node]; the versioned task when the target has versions. */
    fun buildTaskName(node: KnhMpIslandNode): String {
        val title = target.name.replaceFirstChar(Char::uppercase)
        return node.minecraftVersion?.let { "build${title}_${it.replace('.', '_')}" } ?: "build$title"
    }

    fun archiveBaseName(node: KnhMpIslandNode): String =
        "${module.name}-${target.name}" + node.minecraftVersion?.let { "-$it" }.orEmpty()

    fun exportedClasspathFile(node: KnhMpIslandNode): File = nodeBuildDirectory(node).resolve("knhmp/compileClasspath.txt")

    /** Compile classpath of the active node as exported by the island; consumed by the KMP facade only. */
    fun ideClasspath(): FileCollection {
        val export = module.tasks.named(ideClasspathTask)
        return module.files(module.provider {
            exportedClasspathFile(activeNode()).takeIf(File::isFile)?.readLines().orEmpty().filter(String::isNotBlank)
        }).builtBy(export)
    }

    fun registerGradleBuild(taskName: String, group: String, tasks: () -> List<String>): TaskProvider<GradleBuild> =
        module.tasks.register(taskName, GradleBuild::class.java) { task ->
            task.group = group
            task.description = "Runs ${tasks().joinToString()} in the standalone $name build."
            task.dir = directory
            task.buildName = "$buildName-$taskName"
            task.setTasks(tasks())
        }

    // ---- generation helpers shared by all backends ----

    protected fun jvmTarget(node: KnhMpIslandNode): Int =
        extension.sourceSets.effectiveJvmTarget(node.sourceSet, target.bytecodeMinimum)

    protected fun closure(node: KnhMpIslandNode): List<String> = extension.sourceSets.closure(node.sourceSet)

    protected fun kotlinPluginVersion(node: KnhMpIslandNode): String =
        node.configuration.plugin(KOTLIN_PLUGIN)?.version ?: module.getKotlinPluginVersion()

    protected fun requirePluginVersion(node: KnhMpIslandNode, id: String, hint: String): String =
        checkNotNull(node.configuration.plugin(id)?.version) {
            "Target ${target.name} needs the versioned build plugin $id; declare " +
                "knhmp { targets { ${target.name} { plugins { alias($hint) } } } }"
        }

    protected fun pluginLine(id: String, version: String?): String =
        "id(\"${id.escape()}\")" + version?.let { " version \"${it.escape()}\"" }.orEmpty()

    /** Declared plugins that the backend does not place itself. */
    protected fun declaredPluginLines(node: KnhMpIslandNode, vararg backendOwned: String): List<String> =
        node.configuration.plugins.filterNot { it.id in backendOwned }.map { pluginLine(it.id, it.version) }

    /**
     * External dependencies, then module dependencies as dev-jar files followed by the `api`
     * externals of each module. `api` itself is a KnhMP-level notion (propagation to consumers);
     * inside an island it is plain `implementation`, since island jars are consumed as files.
     */
    protected fun dependencyLines(node: KnhMpIslandNode, vararg exclude: String): List<String> {
        fun configuration(name: String) = if (name == KnhMpDependencies.API_CONFIGURATION) "implementation" else name
        fun external(configuration: String, dependency: KnhMpDependencyDeclaration.External, origin: String = "") =
            "add(\"${configuration(configuration).escape()}\", \"${dependency.coordinates.escape()}\")$origin"

        val externals = node.configuration.externalDependencies
            .filterNot { it.configuration in exclude || it.configuration == KnhMpDependencies.TEST_CONFIGURATION }
            .map { external(it.configuration, it) }
        val modules = module.resolvedModuleDependencies(target, node).flatMap { resolved ->
            val path = resolved.island.module.path
            listOf("add(\"${configuration(resolved.configuration).escape()}\", files(\"${resolved.island.devJar(resolved.node).path.escape()}\")) // $path") +
                resolved.apiExternals().map { external(resolved.configuration, it, " // api of $path") }
        }
        return externals + modules
    }

    /** Mounts logical source roots as plain directories; [includeLeaf] is false when Stonecutter owns the leaf. */
    protected fun sourceMountLines(node: KnhMpIslandNode, includeLeaf: Boolean): List<String> {
        val sets = closure(node).filter { includeLeaf || it != node.sourceSet }
        val kotlinDirs = sets.flatMap { module.kotlinSourceRoots(extension, it) }.map { it.path.escape() }
        val resourceDirs = sets.map { module.projectDir.resolve("src/$it/resources").path.escape() }
        return if (includeLeaf) {
            listOf(
                "kotlin.setSrcDirs(listOf(${kotlinDirs.joinToString { "file(\"$it\")" }}))",
                "resources.setSrcDirs(listOf(${resourceDirs.joinToString { "file(\"$it\")" }}))",
            )
        } else {
            kotlinDirs.map { "kotlin.srcDir(file(\"$it\"))" } + resourceDirs.map { "resources.srcDir(file(\"$it\"))" }
        }
    }

    /** Java roots (mixins) of the closure, mounted on the Gradle `main` source set; the leaf's own root when [includeLeaf]. */
    protected fun javaMountScript(node: KnhMpIslandNode, includeLeaf: Boolean): String {
        val dirs = closure(node).filter { includeLeaf || it != node.sourceSet }.map { module.javaSourceRoot(it).path.escape() }
        val call = if (includeLeaf) "java.setSrcDirs(listOf(${dirs.joinToString { "file(\"$it\")" }}))"
        else dirs.joinToString("\n    ") { "java.srcDir(file(\"$it\"))" }
        return "the<SourceSetContainer>().named(\"main\") {\n    $call\n}"
    }

    protected fun mixinsOf(node: KnhMpIslandNode): KnhMpMixins? = node.configuration.mixins

    protected fun refmapName(node: KnhMpIslandNode): String = mixinsOf(node)?.refmap ?: "${extension.modId}.refmap.json"

    protected fun resourceFile(sourceSet: String, resourcePath: String): File = module.projectDir.resolve("src/$sourceSet/resources/$resourcePath")

    /** [toolchain] is false for backends whose convention plugin owns the Java toolchain. */
    private fun kotlinLevelLines(node: KnhMpIslandNode): List<String> = listOfNotNull(
        node.configuration.kotlinApiVersion?.let { "compilerOptions.apiVersion.set(KotlinVersion.fromVersion(\"$it\"))" },
        node.configuration.kotlinLanguageVersion?.let { "compilerOptions.languageVersion.set(KotlinVersion.fromVersion(\"$it\"))" },
    )

    protected fun jvmTargetScript(node: KnhMpIslandNode, toolchain: Boolean = true): String {
        val jvm = jvmTarget(node)
        val toolchainLine = if (toolchain) "kotlin { jvmToolchain(${extension.javaToolchain}) }" else "// Java toolchain is owned by the backend convention plugin."
        return """
            $toolchainLine
            java {
                sourceCompatibility = JavaVersion.toVersion("$jvm")
                targetCompatibility = JavaVersion.toVersion("$jvm")
            }
            tasks.withType<KotlinJvmCompile>().configureEach {
                compilerOptions.jvmTarget.set(JvmTarget.fromTarget("${jvm.asKotlinJvmTarget()}"))
            ${kotlinLevelLines(node).block(4, 12)}
            }
            tasks.named<JavaCompile>("compileJava") { options.release.set($jvm) }
            tasks.named<JavaCompile>("compileTestJava") { options.release.set($jvm) }
        """.trimIndent()
    }

    /** Expands mod identity placeholders in loader metadata files; GTNHGradle does the same for mcmod.info with these keys. */
    protected fun resourceExpansionScript(node: KnhMpIslandNode): String {
        val properties = KnhMpModMetadata.expansionProperties(extension, node.minecraftVersion)
            .entries.joinToString(", ") { (key, value) -> "\"$key\" to \"${value.escape()}\"" }
        val files = KnhMpModMetadata.METADATA_FILES.joinToString(", ") { "\"$it\"" }
        return """
            tasks.named<ProcessResources>("processResources") {
                val metadata = mapOf($properties)
                inputs.properties(metadata)
                filesMatching(listOf($files)) { expand(metadata) }
            }
        """.trimIndent()
    }

    /** The plain-file classpath export the KMP facade reads; [extraFiles] are Kotlin expressions of extra inputs. */
    protected fun exportTaskScript(extraFiles: List<String> = emptyList()): String {
        val inputs = (listOf("tasks.named<KotlinJvmCompile>(\"compileKotlin\").map { it.libraries }") + extraFiles)
            .joinToString(", ")
        return """
            tasks.register("$EXPORT_TASK") {
                group = "knhmp"
                description = "Writes the main compile classpath for the KnhMP IDE facade."
                val classpath = files($inputs)
                val output = layout.buildDirectory.file("knhmp/compileClasspath.txt")
                inputs.files(classpath)
                outputs.file(output)
                doLast { output.get().asFile.writeText(classpath.files.joinToString("\n") { it.absolutePath }) }
            }
        """.trimIndent()
    }

    protected fun settingsPluginManagement(vararg repositories: String): String = """
        pluginManagement {
            repositories {
        ${repositories.map { "maven(\"$it\")" }.plus(listOf("gradlePluginPortal()", "mavenCentral()")).block(8, 8)}
            }
        }
    """.trimIndent()

    protected fun writeGenerated(relativePath: String, content: String) {
        val file = directory.resolve(relativePath)
        file.parentFile.mkdirs()
        if (!file.exists() || file.readText() != content) file.writeText(content)
    }

    protected fun ensureSymbolicLink(link: File, target: File) {
        target.mkdirs()
        link.parentFile.mkdirs()
        if (Files.isSymbolicLink(link.toPath())) {
            check(link.canonicalFile == target.canonicalFile) {
                "Generated KnhMP link $link points to ${link.canonicalFile} instead of ${target.canonicalFile}"
            }
            return
        }
        check(!link.exists()) { "Generated KnhMP path $link exists and is not a symbolic link" }
        Files.createSymbolicLink(link.toPath(), target.toPath())
    }

    protected fun String.escape(): String = replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

    /** Indents generated lines so they survive a template's `trimIndent()` at [extra] spaces. */
    protected fun List<String>.block(extra: Int, template: Int): String =
        joinToString("\n" + " ".repeat(template)) { " ".repeat(extra) + it }

    /** Re-indents a multi-line fragment for interpolation into a template indented by [template]. */
    protected fun String.indent(template: Int): String = lines().block(0, template)

    companion object {
        const val EXPORT_TASK = "exportKnhMpCompileClasspath"
        const val KOTLIN_PLUGIN = "org.jetbrains.kotlin.jvm"
        const val FOOJAY_VERSION = "1.0.0"
        const val HEADER = "// Generated by KnhMP from the module's knhmp { ... } DSL. Do not edit."
        val SCRIPT_IMPORTS = """
            import org.gradle.api.JavaVersion
            import org.gradle.api.tasks.compile.JavaCompile
            import org.jetbrains.kotlin.gradle.dsl.JvmTarget
            import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
            import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
            import org.gradle.language.jvm.tasks.ProcessResources
            import org.gradle.api.tasks.SourceSetContainer
        """.trimIndent()
    }
}
