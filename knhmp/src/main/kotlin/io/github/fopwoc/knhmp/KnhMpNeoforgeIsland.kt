package io.github.fopwoc.knhmp

import org.gradle.api.Project

/**
 * NeoForge through ModDevGradle: one leaf source set, one ModDevGradle generation, a Stonecutter
 * node per Minecraft version. The `neoForge` pseudo-configuration of each node selects its platform
 * version.
 */
internal class KnhMpNeoforgeIsland(
    module: Project,
    extension: KnhMpExtension,
    target: KnhMpTarget,
    name: String,
    nodes: List<KnhMpIslandNode>,
    treeVersions: List<String>,
) : KnhMpStonecutterIsland(module, extension, target, name, nodes, treeVersions) {

    override val pluginRepositories: List<String> = listOf("https://maven.neoforged.net/releases/")

    override fun nodeScript(node: KnhMpIslandNode): String {
        val moddev = requirePluginVersion(node, MODDEV_PLUGIN, "libs.plugins.moddev")
        val neoForge =
            node.configuration.externalDependencies.filter {
                it.configuration == KnhMpDependencies.NEOFORGE_CONFIGURATION
            }
        val neoForgeVersion =
            checkNotNull(neoForge.singleOrNull()?.coordinates?.substringAfterLast(':')) {
                "NeoForge ${node.minecraftVersion} needs exactly one neoForge(...) platform dependency; declared ${neoForge.map { it.coordinates }}"
            }
        val plugins =
            listOf(
                pluginLine(KOTLIN_PLUGIN, kotlinPluginVersion(node)),
                pluginLine(MODDEV_PLUGIN, moddev),
            ) + declaredPluginLines(node, KOTLIN_PLUGIN, MODDEV_PLUGIN, STONECUTTER_PLUGIN)
        return """
            $HEADER
            ${SCRIPT_IMPORTS.indent(12)}
            import org.gradle.api.attributes.java.TargetJvmVersion

            plugins {
            ${plugins.block(4, 12)}
            }

            ${compilerScriptLines(node).block(0, 12)}

            group = "${extension.modGroup.escape()}"
            version = "${extension.modVersion.escape()}"

            ${nodeGuard(node)}

            base { archivesName.set("${archiveBaseName(node).escape()}") }

            repositories {
            ${repositoryLines(node, "maven(\"https://thedarkcolour.github.io/KotlinForForge/\")", "mavenCentral()").block(4, 12)}
            }

            dependencies {
            ${dependencyLines(node, KnhMpDependencies.NEOFORGE_CONFIGURATION).block(4, 12)}
            ${testDependencyLines(node).block(4, 12)}
            }

            neoForge {
                version = "${neoForgeVersion.escape()}"
                runs {
                    create("client") { client() }
                    create("server") {
                        server()
                        programArgument("--nogui")
                    }
                }
                mods {
                    create("${extension.modId.escape()}") { sourceSet(sourceSets.main.get()) }
                }
                // Mixin configs are registered in neoforge.mods.toml; FML applies them with Mojang names, no refmap.
            ${node.configuration.accessTransformers.map { "accessTransformers.from(file(\"${resourceFile(node.sourceSet, it).path.escape()}\"))" }.block(4, 12)}
            }

            // Stonecutter owns the leaf through src/main; parents and tests are versioned links too.
            ${versionedMountScript(node).indent(12)}

            ${jvmTargetScript(node).indent(12)}

            ${kotlinRuntimeScript(node).indent(12)}

            ${bundleConfigurationScript(node, MODERN_KOTLIN_ADAPTER_PROVIDED).indent(12)}
            ${nestedBundleScript(node, "jarJar").indent(12)}

            ${resourceExpansionScript(node).indent(12)}
            // NeoForge 26.x publishes Java 25 variants; a consumer asking for 25 also resolves older lines.
            configurations.matching { it.name in setOf("compileClasspath", "runtimeClasspath") }.configureEach {
                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
            }

            ${exportTaskScript().indent(12)}
        """
            .trimIndent() + "\n"
    }

    companion object {
        const val MODDEV_PLUGIN = "net.neoforged.moddev"
    }
}
