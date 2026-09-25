package io.github.fopwoc.knhmp

import java.io.File
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider

/** A single-project island node for backends that compile one Minecraft version per build. */
internal fun singleNode(extension: KnhMpExtension, target: KnhMpTarget): List<KnhMpIslandNode> {
    val declared = target.minecraftVersions
    check(declared.size <= 1) {
        "Target ${target.name} compiles one Minecraft version per build; declared ${declared.joinToString()}"
    }
    val version = declared.singleOrNull()
    return listOf(
        KnhMpIslandNode(
            version,
            ":",
            target.variantSourceSet(version),
            extension.effectiveConfiguration(target, version),
        )
    )
}

/**
 * Islands are keyed by build-tool environment: the leaf source set (Stonecutter processes exactly
 * one) plus the declared build plugins with their versions (Loom generation, KGP, anything on the
 * plugin classpath). Ordinary mod dependencies never split islands; they differ per node.
 */
private data class IslandKey(val sourceSet: String, val buildPlugins: Map<String, String?>)

internal fun Project.createIslands(extension: KnhMpExtension): List<KnhMpIsland> =
    extension.targets
        .all()
        .flatMap { target ->
            when (target.name) {
                "gtnh" -> listOf(KnhMpGtnhIsland(this, extension, target))
                "neoforge" ->
                    stonecutterIslands(extension, target) { name, nodes ->
                        KnhMpNeoforgeIsland(this, extension, target, name, nodes)
                    }
                "fabric" ->
                    stonecutterIslands(extension, target) { name, nodes ->
                        KnhMpFabricIsland(this, extension, target, name, nodes)
                    }
                else -> error("Unsupported KnhMP target ${target.name}")
            }
        }
        .also { islands -> extensions.extraProperties.set(ISLANDS_PROPERTY, islands) }

private const val ISLANDS_PROPERTY = "knhmp.islands"

/** Islands of another KnhMP module in this build; forces its evaluation first. */
internal fun Project.islandsOf(modulePath: String): List<KnhMpIsland> {
    val other =
        rootProject.findProject(modulePath)
            ?: error("KnhMP module dependency $modulePath does not exist")
    evaluationDependsOn(modulePath)
    val islands =
        checkNotNull(other.extensions.extraProperties.properties[ISLANDS_PROPERTY] as? List<*>) {
            "$modulePath is not a KnhMP module"
        }
    return islands.map { island ->
        island as? KnhMpIsland
            ?: error(
                "$modulePath loaded KnhMP in a different plugin classloader than $path; declare " +
                    "id(\"io.github.fopwoc.knhmp\") and the other module plugins with `apply false` in the root build script"
            )
    }
}

/**
 * Resolves a module dependency of [node] to the other module's node with the same target and
 * Minecraft version; the matrix must line up, there is no fallback.
 */
internal fun Project.dependencyNode(
    target: KnhMpTarget,
    node: KnhMpIslandNode,
    modulePath: String,
): Pair<KnhMpIsland, KnhMpIslandNode> {
    val candidates = islandsOf(modulePath).filter { it.target.name == target.name }
    check(candidates.isNotEmpty()) {
        "$modulePath declares no ${target.name} target, required by $path"
    }
    return candidates.firstNotNullOfOrNull { island ->
        island.nodes
            .firstOrNull { it.minecraftVersion == node.minecraftVersion }
            ?.let { island to it }
    }
        ?: error(
            "$modulePath declares no ${target.name} ${node.minecraftVersion ?: ""} variant, required by $path"
        )
}

/** One resolved module dependency of a node: the producing node and how the consumer sees it. */
internal class KnhMpResolvedModuleDependency(
    val configuration: String,
    val island: KnhMpIsland,
    val node: KnhMpIslandNode,
)

/**
 * Module dependencies of [node] including everything reachable through the other modules' `api`
 * module dependencies. A direct hop keeps its declared configuration; transitive hops inherit it.
 */
internal fun Project.resolvedModuleDependencies(
    target: KnhMpTarget,
    node: KnhMpIslandNode,
): List<KnhMpResolvedModuleDependency> {
    val result = LinkedHashMap<String, KnhMpResolvedModuleDependency>()
    fun visit(from: Project, fromNode: KnhMpIslandNode, inherited: String?) {
        fromNode.configuration.moduleDependencies
            .filter { inherited == null || it.configuration == KnhMpDependencies.API_CONFIGURATION }
            .forEach { dependency ->
                val (island, other) = from.dependencyNode(target, fromNode, dependency.path)
                val configuration = inherited ?: dependency.configuration
                if (
                    result.putIfAbsent(
                        island.module.path,
                        KnhMpResolvedModuleDependency(configuration, island, other),
                    ) == null
                ) {
                    visit(island.module, other, configuration)
                }
            }
    }
    visit(this, node, null)
    return result.values.toList()
}

/** Externals other modules expose with `api`, for the consumer node's same target/version. */
internal fun KnhMpResolvedModuleDependency.apiExternals():
    List<KnhMpDependencyDeclaration.External> =
    node.configuration.externalDependencies.filter {
        it.configuration in KnhMpDependencies.API_CONFIGURATIONS
    }

/** Root-build tasks that produce the other modules' jars a set of nodes depends on. */
internal fun Project.dependencyBuildTasks(
    target: KnhMpTarget,
    nodes: List<KnhMpIslandNode>,
): List<String> =
    nodes
        .flatMap { node ->
            resolvedModuleDependencies(target, node).map {
                "${it.island.module.path}:${it.island.buildTaskName(it.node)}"
            }
        }
        .distinct()

private fun stonecutterIslands(
    extension: KnhMpExtension,
    target: KnhMpTarget,
    create: (name: String, nodes: List<KnhMpIslandNode>) -> KnhMpStonecutterIsland,
): List<KnhMpIsland> {
    check(target.minecraftVersions.isNotEmpty()) {
        "Target ${target.name} declares no minecraft(...) versions"
    }
    val nodesByKey =
        target.minecraftVersions
            .map { version ->
                val configuration = extension.effectiveConfiguration(target, version)
                KnhMpIslandNode(
                    version,
                    ":$version",
                    target.variantSourceSet(version),
                    configuration,
                )
            }
            .groupBy { node ->
                IslandKey(
                    node.sourceSet,
                    node.configuration.plugins.associate { it.id to it.version },
                )
            }

    val usedNames = mutableSetOf<String>()
    return nodesByKey.map { (key, nodes) ->
        val family =
            key.sourceSet.removePrefix(target.name).removeSuffix("Main").lowercase(Locale.ROOT)
        val base = if (family.isEmpty()) target.name else "${target.name}-$family"
        val name =
            generateSequence(1) { it + 1 }
                .map { if (it == 1) base else "$base-$it" }
                .first(usedNames::add)
        create(name, nodes)
    }
}

internal fun Project.configureIslands(extension: KnhMpExtension, islands: List<KnhMpIsland>) {
    islands.forEach { it.generate() }
    islands.forEach { island ->
        island
            .registerGradleBuild(island.ideClasspathTask, "knhmp") {
                listOf(island.activeNode().task(KnhMpIsland.EXPORT_TASK))
            }
            .configure {
                it.dependsOn(dependencyBuildTasks(island.target, listOf(island.activeNode())))
            }
    }

    val buildTasks =
        extension.targets.all().associateWith { target ->
            registerTargetTasks(target, islands.filter { it.target == target })
        }

    val buildAll =
        tasks.register("buildAll", Sync::class.java) { task ->
            task.group = "build"
            task.description =
                "Builds every KnhMP target and variant in its island and collects the mod jars."
            task.into(layout.buildDirectory.dir("libs"))
            task.dependsOn(buildTasks.values)
            islands.forEach { island ->
                island.nodes.forEach { node -> task.from(island.jar(node)) }
            }
        }
    tasks.named("build").configure { task -> task.setDependsOn(listOf(buildAll)) }

    registerRootAggregates(extension)
}

private fun Project.registerTargetTasks(
    target: KnhMpTarget,
    islands: List<KnhMpIsland>,
): TaskProvider<*> {
    val title = target.name.replaceFirstChar { it.titlecase(Locale.ROOT) }
    val versioned = islands.any { island -> island.nodes.any { it.minecraftVersion != null } }

    val islandBuilds = islands.map { island ->
        island
            .registerGradleBuild(
                if (islands.size > 1) "build${island.title}Island" else "build$title",
                "build",
            ) {
                island.nodes.map { it.task(target.buildTask) }
            }
            .also {
                it.configure { task -> task.dependsOn(dependencyBuildTasks(target, island.nodes)) }
            }
    }
    val buildTarget =
        if (islands.size == 1) islandBuilds.single()
        else
            tasks.register("build$title") { task ->
                task.group = "build"
                task.description = "Builds every ${target.name} variant across its islands."
                task.dependsOn(islandBuilds)
            }

    if (versioned) {
        islands.forEach { island ->
            island.nodes.forEach { node ->
                val suffix = checkNotNull(node.minecraftVersion).replace('.', '_')
                val upstream = dependencyBuildTasks(target, listOf(node))
                island
                    .registerGradleBuild("build${title}_$suffix", "build") {
                        listOf(node.task(target.buildTask))
                    }
                    .configure { it.dependsOn(upstream) }
                island
                    .registerGradleBuild("run${title}_${suffix}Client", "knhmp") {
                        listOf(node.task(target.runTask))
                    }
                    .configure { it.dependsOn(upstream) }
                if (island is KnhMpStonecutterIsland) {
                    island
                        .registerGradleBuild("use${title}_$suffix", "knhmp") {
                            listOf("Set active project to ${node.minecraftVersion}")
                        }
                        .configure { task ->
                            task.doLast {
                                activeVariantFile(target).writeText(node.minecraftVersion)
                            }
                        }
                }
            }
        }
        val active = activeVariant(target, islands)
        active.island
            .registerGradleBuild("run${title}Client", "knhmp") {
                listOf(active.node.task(target.runTask))
            }
            .configure { it.dependsOn(dependencyBuildTasks(target, listOf(active.node))) }
    } else {
        val island = islands.single()
        island
            .registerGradleBuild("run${title}Client", "knhmp") {
                listOf(island.nodes.single().task(target.runTask))
            }
            .configure { it.dependsOn(dependencyBuildTasks(target, island.nodes)) }
    }
    return buildTarget
}

private class ActiveVariant(val island: KnhMpIsland, val node: KnhMpIslandNode)

private fun Project.activeVariantFile(target: KnhMpTarget): File =
    projectDir.resolve(".knhmp/active-${target.name}")

/**
 * The variant unversioned run tasks follow. `use<Target>_<v>` records it; Stonecutter's own
 * per-island `stonecutter active` follows the same switch, so preprocessing and IDE classpaths
 * agree.
 */
private fun Project.activeVariant(target: KnhMpTarget, islands: List<KnhMpIsland>): ActiveVariant {
    val recorded = activeVariantFile(target).takeIf(File::isFile)?.readText()?.trim()
    islands.forEach { island ->
        island.nodes
            .firstOrNull { it.minecraftVersion == recorded }
            ?.let {
                return ActiveVariant(island, it)
            }
    }
    val island = islands.last()
    return ActiveVariant(island, island.activeNode())
}

private fun Project.registerRootAggregates(extension: KnhMpExtension) {
    if (this == rootProject) return
    val moduleTasks = tasks
    val modulePath = path

    fun aggregate(name: String, group: String) {
        rootProject.tasks.maybeCreate(name).apply {
            this.group = group
            dependsOn("$modulePath:$name")
        }
    }

    // Build and switch tasks fan out to every module; run tasks launch one game and stay
    // module-qualified (`:hello:runFabricClient`).
    aggregate("buildAll", "build")
    extension.targets.all().forEach { target ->
        val title = target.name.replaceFirstChar { it.titlecase(Locale.ROOT) }
        aggregate("build$title", "build")
        moduleTasks.names
            .filter { it.startsWith("build${title}_") || it.startsWith("use${title}_") }
            .forEach { name -> aggregate(name, if (name.startsWith("build")) "build" else "knhmp") }
    }
    rootProject.tasks.named("build").configure { task -> task.dependsOn("buildAll") }
}
