package io.github.fopwoc.knhmp

import groovy.json.JsonSlurper
import java.util.jar.JarFile
import org.gradle.api.Project

/**
 * Verifies the built artifacts, where registration/refmap failures are otherwise invisible to
 * compilation.
 */
internal fun Project.registerMixinVerification(
    extension: KnhMpExtension,
    islands: List<KnhMpIsland>,
) {
    tasks.register("verifyMixinArtifacts") { task ->
        task.group = "verification"
        task.description =
            "Checks Mixin classes, loader registration, and refmaps in every enabled target jar."
        task.dependsOn("buildAll")
        task.doLast {
            islands.forEach { island ->
                island.nodes
                    .filter { it.configuration.mixins != null }
                    .forEach { node ->
                        verifyMixinArtifact(extension, island, node)
                    }
            }
        }
    }
}

private fun Project.verifyMixinArtifact(
    extension: KnhMpExtension,
    island: KnhMpIsland,
    node: KnhMpIslandNode,
) {
    val mixins = checkNotNull(node.configuration.mixins)
    val packageName = checkNotNull(mixins.packageName)
    val jar = layout.buildDirectory.file("libs/${island.jar(node).name}").get().asFile
    JarFile(jar).use { archive ->
        val classPrefix = packageName.replace('.', '/') + "/"
        val containsMixinClass =
            archive.entries().asSequence().any {
                !it.isDirectory && it.name.startsWith(classPrefix) && it.name.endsWith(".class")
            }
        check(containsMixinClass) {
            "${jar.name} contains no Mixin classes under $packageName"
        }

        val configNames =
            when (island.target.name) {
                "gtnh" -> {
                    val expected = "mixins.${extension.modId}.json"
                    val declared =
                        archive.manifest.mainAttributes
                            .getValue("MixinConfigs")
                            ?.split(',')
                            ?.map(String::trim)
                            .orEmpty()
                    check(expected in declared) {
                        "${jar.name} manifest does not register $expected: $declared"
                    }
                    listOf(expected)
                }
                "fabric" -> fabricMixinConfigs(archive)
                "neoforge" -> neoForgeMixinConfigs(archive)
                else -> error("Unsupported Mixin target ${island.target.name}")
            }

        val matching = configNames.filter { configName ->
            val config = archive.jsonObject(configName)
            config["package"] == packageName
        }
        check(matching.size == 1) {
            "${jar.name} registers ${matching.size} Mixin configs for $packageName; candidates: $configNames"
        }

        if (
            island.target.name == "gtnh" ||
                island.target.name == "fabric" && KnhMpFabricIsland.needsRefmap(node)
        ) {
            val expectedRefmap =
                if (island.target.name == "gtnh") {
                    "mixins.${extension.modId}.refmap.json"
                } else {
                    mixins.refmap ?: "${extension.modId}.refmap.json"
                }
            val config = archive.jsonObject(matching.single())
            check(config["refmap"] == expectedRefmap) {
                "${jar.name} Mixin config ${matching.single()} refers to ${config["refmap"]} instead of $expectedRefmap"
            }
            check(archive.getJarEntry(expectedRefmap) != null) {
                "${jar.name} lacks generated Mixin refmap $expectedRefmap"
            }
        }
    }
    logger.lifecycle("{} registers Mixins for {}", jar.name, packageName)
}

private fun fabricMixinConfigs(archive: JarFile): List<String> {
    val metadata = archive.jsonObject("fabric.mod.json")
    return (metadata["mixins"] as? List<*>).orEmpty().map { declaration ->
        when (declaration) {
            is String -> declaration
            is Map<*, *> ->
                checkNotNull(declaration["config"] as? String) {
                    "Invalid Fabric Mixin declaration: $declaration"
                }
            else -> error("Invalid Fabric Mixin declaration: $declaration")
        }
    }
}

private fun neoForgeMixinConfigs(archive: JarFile): List<String> =
    MIXIN_TOML_BLOCK.findAll(archive.text("META-INF/neoforge.mods.toml"))
        .map { it.groupValues[1] }
        .toList()

@Suppress("UNCHECKED_CAST")
private fun JarFile.jsonObject(path: String): Map<String, Any?> =
    JsonSlurper().parseText(text(path)) as Map<String, Any?>

private fun JarFile.text(path: String): String {
    val entry = checkNotNull(getJarEntry(path)) { "$name lacks $path" }
    return getInputStream(entry).bufferedReader().use { it.readText() }
}

private val MIXIN_TOML_BLOCK = Regex("""(?m)^\s*\[\[mixins]]\s*$\s*config\s*=\s*"([^"]+)"\s*$""")
