package io.github.fopwoc.knhmp

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.plugin.use.PluginDependency

/** Build plugins applied to a generated compiler project, declared with Gradle plugin notation. */
class KnhMpPlugins {

    private val declarations = LinkedHashMap<String, () -> KnhMpPluginDeclaration>()

    fun alias(plugin: Provider<PluginDependency>) {
        declare(plugin.get().pluginId) { plugin.get().toDeclaration() }
    }

    fun alias(plugin: ProviderConvertible<PluginDependency>) = alias(plugin.asProvider())

    fun id(id: String, version: String? = null) {
        declare(id) { KnhMpPluginDeclaration(id, version) }
    }

    private fun declare(id: String, declaration: () -> KnhMpPluginDeclaration) {
        declarations[id] = declaration
    }

    internal fun resolve(): List<KnhMpPluginDeclaration> = declarations.values.map { it() }

    private fun PluginDependency.toDeclaration() =
        KnhMpPluginDeclaration(pluginId, version.requiredVersion.ifEmpty { null })
}

data class KnhMpPluginDeclaration(val id: String, val version: String?)
