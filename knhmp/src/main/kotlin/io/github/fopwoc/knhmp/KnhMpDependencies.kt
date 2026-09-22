package io.github.fopwoc.knhmp

import org.gradle.api.Action
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible

/**
 * Dependencies of a generated compiler project. Notation is anything Gradle's `DependencyHandler`
 * accepts for external modules — `"group:name:version"` strings and Version Catalog accessors —
 * plus other KnhMP modules as `":framework"` paths or type-safe `projects.framework` accessors
 * (`enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`).
 */
class KnhMpDependencies {

    private val declarations = mutableListOf<Pair<String, Any>>()
    private val modules = mutableListOf<Pair<String, String>>()

    /** Generic escape hatch for any configuration the backend plugins register. */
    fun add(configuration: String, notation: Any) {
        val project = projectDependencyOf(notation)
        if (project != null) module(project, configuration) else declarations += configuration to notation
    }

    /**
     * Another KnhMP module of this build, e.g. `":framework"`. Each compiler variant depends on that
     * module's variant for the same target and Minecraft version, so the other module must declare
     * the same matrix for every target this scope applies to.
     */
    fun module(path: String, configuration: String = "implementation") {
        require(path.startsWith(":")) { "KnhMP module dependencies use Gradle project paths, got $path" }
        modules += configuration to path
    }

    /** Type-safe project accessor form: `module(projects.framework)`. */
    fun module(project: ProjectDependency, configuration: String = "implementation") = module(project.path, configuration)

    private fun projectDependencyOf(notation: Any): ProjectDependency? = when (notation) {
        is ProjectDependency -> notation
        is Provider<*> -> notation.orNull?.let(::projectDependencyOf)
        is ProviderConvertible<*> -> projectDependencyOf(notation.asProvider())
        else -> null
    }

    /** Mirrors Gradle's `variantOf`: `compileOnly(variantOf(libs.opis) { classifier("dev") })`. */
    fun variantOf(dependency: Any, configure: Action<in KnhMpVariantSpec>): Any =
        KnhMpVariantSpec(dependency).also(configure::execute)

    fun implementation(notation: Any) = add("implementation", notation)

    /** Part of this module's API: consumers of the module get it (and its own `api` graph) transitively. */
    fun api(notation: Any) = add(API_CONFIGURATION, notation)
    fun compileOnly(notation: Any) = add("compileOnly", notation)
    fun runtimeOnly(notation: Any) = add("runtimeOnly", notation)

    /**
     * Test dependency for this scope. Module-scope declarations belong to `commonTest`; target and
     * variant declarations belong to the corresponding loader test source set.
     */
    fun testImplementation(notation: Any) = add(TEST_CONFIGURATION, notation)

    /** Loom mod configurations; the dependency is remapped and visible to the dev runtime. */
    fun modImplementation(notation: Any) = add("modImplementation", notation)
    fun modCompileOnly(notation: Any) = add("modCompileOnly", notation)
    fun modRuntimeOnly(notation: Any) = add("modRuntimeOnly", notation)

    /** ModDevGradle's NeoForge platform; projected into `neoForge { version = ... }`. */
    fun neoForge(notation: Any) = add(NEOFORGE_CONFIGURATION, notation)

    internal fun resolve(): List<KnhMpDependencyDeclaration> =
        declarations.map { (configuration, notation) ->
            KnhMpDependencyDeclaration.External(configuration, coordinatesOf(notation))
        } + modules.map { (configuration, path) -> KnhMpDependencyDeclaration.Module(configuration, path) }

    private fun coordinatesOf(notation: Any): String = when (notation) {
        is String -> notation
        is KnhMpVariantSpec -> coordinatesOf(notation.dependency) + notation.classifier?.let { ":$it" }.orEmpty()
        is Provider<*> -> coordinatesOf(notation.get())
        is ProviderConvertible<*> -> coordinatesOf(notation.asProvider().get())
        is MinimalExternalModuleDependency ->
            "${notation.module.group}:${notation.module.name}:${notation.versionConstraint.selected()}${notation.classifierSuffix()}"
        is ExternalModuleDependency -> "${notation.group}:${notation.name}:${notation.version}${notation.classifierSuffix()}"
        else -> error("Unsupported KnhMP dependency notation: $notation (${notation::class.qualifiedName})")
    }

    private fun ExternalModuleDependency.classifierSuffix(): String =
        artifacts.singleOrNull()?.classifier?.let { ":$it" }.orEmpty()

    private fun VersionConstraint.selected(): String =
        listOf(strictVersion, requiredVersion, preferredVersion).firstOrNull { it.isNotEmpty() }
            ?: error("Version Catalog dependency without a version: $this")

    internal companion object {
        const val NEOFORGE_CONFIGURATION = "neoForge"
        const val TEST_CONFIGURATION = "testImplementation"
        const val API_CONFIGURATION = "api"
    }
}

sealed interface KnhMpDependencyDeclaration {
    val configuration: String

    data class External(override val configuration: String, val coordinates: String) : KnhMpDependencyDeclaration
    data class Module(override val configuration: String, val path: String) : KnhMpDependencyDeclaration
}

/** Artifact selection of one dependency; only a classifier is needed by current backends. */
class KnhMpVariantSpec internal constructor(internal val dependency: Any) {
    internal var classifier: String? = null
        private set

    fun classifier(classifier: String) {
        this.classifier = classifier
    }
}
