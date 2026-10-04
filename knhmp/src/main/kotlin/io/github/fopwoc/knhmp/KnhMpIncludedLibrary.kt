package io.github.fopwoc.knhmp

import java.io.File
import org.gradle.api.Project

/** A library a module compiles from sources, resolved and checked once per module. */
internal class KnhMpIncludedLibrary(val project: Project, val baseline: KnhMpLibraryBaseline) {
    val kotlinRoot: File
        get() = project.projectDir.resolve("src/main/kotlin")

    val resourceRoot: File
        get() = project.projectDir.resolve("src/main/resources")
}

/** Libraries the given source sets include, in source-set order, each once. */
internal fun Project.includedLibraries(
    extension: KnhMpExtension,
    sourceSets: List<String>,
): List<KnhMpIncludedLibrary> =
    sourceSets
        .flatMap { extension.sourceSets.sourceSet(it).includes() }
        .distinct()
        .map(::includedLibrary)

private fun Project.includedLibrary(path: String): KnhMpIncludedLibrary {
    val library =
        rootProject.findProject(path) ?: error("Included KnhMP library $path does not exist")
    evaluationDependsOn(path)
    val baseline =
        checkNotNull(KnhMpLibraryBaseline.of(library)) {
            "$path is not a KnhMP library; apply id(\"io.github.fopwoc.knhmp.library\") there"
        }
    val foreign =
        STANDALONE_CONFIGURATIONS.flatMap { name ->
                library.configurations.findByName(name)?.dependencies.orEmpty().filterNot {
                    it.group == KOTLIN_GROUP
                }
            }
            .map { "${it.group}:${it.name}" }
    check(foreign.isEmpty()) {
        "KnhMP library $path depends on ${foreign.joinToString()}; included libraries compile " +
            "inside each island from sources and may only use the Kotlin stdlib"
    }
    return KnhMpIncludedLibrary(library, baseline)
}

/**
 * Every island must be able to run what its included libraries were built for: no newer bytecode,
 * no newer stdlib API than the island's loader provides.
 */
internal fun Project.verifyIncludedLibraries(
    extension: KnhMpExtension,
    islands: List<KnhMpIsland>,
) {
    val problems = islands.flatMap { island ->
        island.nodes.flatMap { node ->
            val islandJvm = island.jvmTarget(node)
            val islandApi = node.configuration.kotlinApiVersion
            includedLibraries(extension, extension.sourceSets.closure(node.sourceSet)).flatMap {
                library ->
                val where =
                    "${library.project.path} in ${island.name} ${node.minecraftVersion.orEmpty()}"
                        .trim()
                listOfNotNull(
                    "$where: library targets Java ${library.baseline.jvmTarget}, island targets Java $islandJvm"
                        .takeIf { library.baseline.jvmTarget > islandJvm },
                    "$where: library uses Kotlin API ${library.baseline.apiVersion}, island provides $islandApi"
                        .takeIf { islandApi != null && library.baseline.newerApiThan(islandApi) },
                )
            }
        }
    }
    check(problems.isEmpty()) {
        "Included libraries need a newer runtime than an island provides:\n" +
            problems.joinToString("\n")
    }
}

/**
 * Unpinned libraries compile against the newest stdlib, so they are newer than any pinned level.
 */
private fun KnhMpLibraryBaseline.newerApiThan(level: String): Boolean {
    val own = apiVersion ?: return true
    fun parts(version: String) = version.split('.').map(String::toInt)
    val (major, minor) = parts(own)
    val (otherMajor, otherMinor) = parts(level)
    return major > otherMajor || (major == otherMajor && minor > otherMinor)
}

private const val KOTLIN_GROUP = "org.jetbrains.kotlin"

private val STANDALONE_CONFIGURATIONS =
    listOf("api", "implementation", "compileOnly", "runtimeOnly")
