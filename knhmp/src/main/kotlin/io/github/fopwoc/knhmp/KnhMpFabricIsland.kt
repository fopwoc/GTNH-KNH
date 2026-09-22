package io.github.fopwoc.knhmp

import org.gradle.api.Project

/** A Fabric compatibility family: one leaf source set and one Loom generation. */
internal class KnhMpFabricIsland(
    module: Project,
    extension: KnhMpExtension,
    target: KnhMpTarget,
    name: String,
    nodes: List<KnhMpIslandNode>,
) : KnhMpStonecutterIsland(module, extension, target, name, nodes) {

    override val pluginRepositories: List<String> = listOf("https://maven.fabricmc.net/")

    /** Remapping Loom ships the intermediary jar in `libs` and keeps the named one in `devlibs` as `-dev`. */
    override fun devJar(node: KnhMpIslandNode) =
        if (isObfuscated(checkNotNull(node.minecraftVersion))) {
            nodeBuildDirectory(node).resolve("devlibs/${jar(node).name.replace(".jar", "-dev.jar")}")
        } else {
            jar(node)
        }

    override fun nodeScript(node: KnhMpIslandNode): String {
        val version = checkNotNull(node.minecraftVersion)
        val loom = node.configuration.plugins.filter { it.id in LOOM_PLUGIN_IDS && it.version != null }
        check(loom.size == 1) {
            "Fabric $version needs exactly one versioned Loom plugin (${LOOM_PLUGIN_IDS.joinToString(" or ")}); " +
                "declared ${loom.map { "${it.id}:${it.version}" }}. Declare it per compatibility family: " +
                "minecraft(\"$version\") { plugins { alias(libs.plugins.loom) } }"
        }
        val plugins = listOf(pluginLine(KOTLIN_PLUGIN, kotlinPluginVersion(node)), pluginLine(loom.single().id, loom.single().version)) +
            declaredPluginLines(node, KOTLIN_PLUGIN, *LOOM_PLUGIN_IDS.toTypedArray(), STONECUTTER_PLUGIN)
        // Obfuscated versions compile against Mojang mappings through Loom's remapping pipeline.
        val mappings = if (isObfuscated(version)) "mappings(loom.officialMojangMappings())"
        else "// Minecraft $version ships unobfuscated and needs no mappings artifact."
        return """
            $HEADER
            ${SCRIPT_IMPORTS.indent(12)}

            plugins {
            ${plugins.block(4, 12)}
            }

            ${compilerScriptLines(node).block(0, 12)}

            group = "${extension.modGroup.escape()}"
            version = "${extension.modVersion.escape()}"

            ${nodeGuard(node)}

            base { archivesName.set("${archiveBaseName(node).escape()}") }

            repositories {
            ${repositoryLines(node, "mavenCentral()").block(4, 12)}
            }

            dependencies {
                minecraft("com.mojang:minecraft:$version")
                $mappings
            ${dependencyLines(node).block(4, 12)}
            ${testDependencyLines(node).block(4, 12)}
            }

            loom {
                mods {
                    create("${extension.modId.escape()}") { sourceSet(sourceSets.main.get()) }
                }
            ${loomMixinLines(node).block(4, 12)}
            }

            // Stonecutter owns the leaf source set through src/main; parents of the logical closure mount directly.
            kotlin {
                sourceSets.named("main") {
            ${sourceMountLines(node, includeLeaf = false).block(8, 12)}
                }
            }
            ${javaMountScript(node, includeLeaf = false).indent(12)}
            ${testMountScript(node).indent(12)}

            ${jvmTargetScript(node).indent(12)}

            ${kotlinRuntimeScript(node).indent(12)}

            ${bundleConfigurationScript(node, MODERN_KOTLIN_ADAPTER_PROVIDED).indent(12)}
            ${nestedBundleScript(node, "include").indent(12)}

            ${resourceExpansionScript(node).indent(12)}

            ${exportTaskScript().indent(12)}
        """.trimIndent() + "\n"
    }

    /**
     * Loom applies the Mixin annotation processor by itself; obfuscated nodes need a refmap name.
     * Mixin configs are registered by the module in `fabric.mod.json`. An access widener is
     * declared there too and handed to Loom so the dev jar is widened for compilation.
     */
    private fun loomMixinLines(node: KnhMpIslandNode): List<String> = buildList {
        if (mixinsOf(node) != null && isObfuscated(checkNotNull(node.minecraftVersion))) {
            add("mixin { defaultRefmapName.set(\"${refmapName(node).escape()}\") }")
        }
        node.configuration.accessWidener?.let {
            add("accessWidenerPath.set(file(\"${resourceFile(node.sourceSet, it).path.escape()}\"))")
        }
    }

    companion object {
        /** Loom renamed its plugin id in 1.14; both ids denote the same build tool. */
        val LOOM_PLUGIN_IDS = setOf("fabric-loom", "net.fabricmc.fabric-loom")
    }
}
