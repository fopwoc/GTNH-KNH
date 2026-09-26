package io.github.fopwoc.knhmp

import org.gradle.api.Action

/**
 * A configuration-only scope: build plugins and dependencies applied to generated compiler
 * projects. A scope never creates source sets, source directories, or IDE nodes.
 */
open class KnhMpBuildScope {

    val plugins = KnhMpPlugins()
    val dependencies = KnhMpDependencies()
    val kotlin = KnhMpKotlinOptions()
    val mixins = KnhMpMixins()
    internal val accessTransformers = mutableListOf<String>()
    internal val compilerScripts = mutableListOf<String>()
    internal var accessWidener: String? = null

    /**
     * Lowest bytecode level of this scope's jars, e.g. `25` for a Minecraft version that needs Java
     * 25 anyway. The source closure's own targets can still raise it; the narrowest scope wins.
     */
    var jvmTarget: Int? = null
        set(value) {
            require(value == null || value >= 8) { "jvmTarget must be at least 8: $value" }
            field = value
        }

    fun plugins(configure: Action<in KnhMpPlugins>) = configure.execute(plugins)

    fun dependencies(configure: Action<in KnhMpDependencies>) = configure.execute(dependencies)

    fun kotlin(configure: Action<in KnhMpKotlinOptions>) = configure.execute(kotlin)

    fun mixins(configure: Action<in KnhMpMixins>) = configure.execute(mixins)

    /** Forge-style access transformer files, resource paths such as `META-INF/<modId>_at.cfg`. */
    fun accessTransformers(vararg resourcePaths: String) {
        accessTransformers += resourcePaths
    }

    /** Fabric access widener, a resource path such as `<modId>.accesswidener`. */
    fun accessWidener(resourcePath: String) {
        accessWidener = resourcePath
    }

    /**
     * Applies a module-owned Gradle script inside each matching generated compiler project after
     * its plugins are available and before dependencies are declared. This is the escape hatch for
     * cohesive module-specific packaging and code generation that does not belong in a backend.
     */
    fun compilerScript(path: String) {
        compilerScripts += path
    }
}

/**
 * Mixin settings. The Mixin runtime comes from the loader (Fabric Loader, FML) or, on 1.7.10, from
 * UniMixins which GTNHGradle adds; KnhMP only registers configs and refmaps with each build tool.
 * Mixin configs themselves are ordinary resources listed in your `fabric.mod.json` / `mods.toml`;
 * GTNHGradle generates `mixins.<modId>.json` from [packageName].
 */
class KnhMpMixins {
    /** Java package holding the mixin classes; GTNHGradle requires it below `modGroup`. */
    var packageName: String? = null

    /** Optional `IMixinConfigPlugin` class name (GTNHGradle `mixinPlugin`). */
    var plugin: String? = null

    /**
     * Refmap file name for obfuscated nodes of the legacy `fabric-loom` plugin; defaults to
     * `<modId>.refmap.json`. The remapping plugin needs none.
     */
    var refmap: String? = null

    var debug: Boolean = false

    internal val enabled: Boolean
        get() = packageName != null
}

/**
 * Kotlin language settings of a compiler variant. The runtime stdlib is provided by the loader's
 * Kotlin adapter (Forgelin, fabric-language-kotlin, KotlinForForge) and can be older than the
 * compiler, so shared code must be checked against the oldest `apiVersion` of any target.
 */
class KnhMpKotlinOptions {
    /**
     * Kotlin stdlib the loader's Kotlin adapter provides at runtime, e.g. `"2.1.10"` for Forgelin.
     * Compile and runtime classpaths are pinned to it, and it implies [apiVersion].
     */
    var stdlibVersion: String? = null

    /**
     * Kotlin stdlib API level the produced bytecode may use, e.g. `"2.1"`; defaults from
     * [stdlibVersion].
     */
    var apiVersion: String? = null

    /** Kotlin language level; defaults to [apiVersion] when set. */
    var languageVersion: String? = null
}

/** Materialized plugin and dependency coordinates for one concrete compiler variant. */
internal data class KnhMpEffectiveConfiguration(
    val plugins: List<KnhMpPluginDeclaration>,
    val dependencies: List<KnhMpDependencyDeclaration>,
    val exclusions: List<KnhMpExclusion>,
    val kotlinStdlibVersion: String?,
    val kotlinApiVersion: String?,
    val kotlinLanguageVersion: String?,
    val mixins: KnhMpMixins?,
    val accessTransformers: List<String>,
    val accessWidener: String?,
    val jvmTarget: Int?,
    val compilerScripts: List<String>,
    /**
     * Test dependencies of the node's whole test closure, from `commonTest` down to the loader test
     * set.
     */
    val testDependencies: List<KnhMpDependencyDeclaration>,
) {
    fun plugin(id: String): KnhMpPluginDeclaration? = plugins.firstOrNull { it.id == id }

    val externalDependencies: List<KnhMpDependencyDeclaration.External>
        get() = dependencies.filterIsInstance<KnhMpDependencyDeclaration.External>()

    val moduleDependencies: List<KnhMpDependencyDeclaration.Module>
        get() = dependencies.filterIsInstance<KnhMpDependencyDeclaration.Module>()

    val bundledDependencies: List<KnhMpDependencyDeclaration.External>
        get() = externalDependencies.filter {
            it.configuration == KnhMpDependencies.BUNDLE_CONFIGURATION
        }
}

/**
 * Module scope + target scope + matching `minecraft(version)` scope; narrower plugins replace wider
 * ones by id.
 */
internal fun KnhMpExtension.effectiveConfiguration(
    target: KnhMpTarget,
    minecraftVersion: String?,
): KnhMpEffectiveConfiguration {
    val scopes = listOf(common, target) + listOfNotNull(minecraftVersion?.let(target::variantScope))
    val plugins =
        scopes
            .flatMap { it.plugins.resolve() }
            .associateByTo(LinkedHashMap()) { it.id }
            .values
            .toList()
    val stdlibVersion = scopes.mapNotNull { it.kotlin.stdlibVersion }.lastOrNull()
    val apiVersion =
        scopes.mapNotNull { it.kotlin.apiVersion }.lastOrNull()
            ?: stdlibVersion?.split('.')?.take(2)?.joinToString(".")
    val languageVersion = scopes.mapNotNull { it.kotlin.languageVersion }.lastOrNull() ?: apiVersion
    return KnhMpEffectiveConfiguration(
        plugins,
        scopes.flatMap { it.dependencies.resolve() },
        scopes.flatMap { it.dependencies.exclusions() }.distinct(),
        stdlibVersion,
        apiVersion,
        languageVersion,
        mixins = scopes.map { it.mixins }.lastOrNull { it.enabled },
        accessTransformers = scopes.flatMap { it.accessTransformers },
        accessWidener = scopes.mapNotNull { it.accessWidener }.lastOrNull(),
        jvmTarget = scopes.mapNotNull { it.jvmTarget }.lastOrNull(),
        compilerScripts = scopes.flatMap { it.compilerScripts },
        testDependencies =
            scopes
                .flatMap { it.dependencies.resolve() }
                .filter { it.configuration == KnhMpDependencies.TEST_CONFIGURATION },
    )
}

/**
 * Bytecode level of one variant's jar: the source closure's target, the backend minimum, and the
 * scopes' [KnhMpBuildScope.jvmTarget] floor, whichever is highest.
 */
internal fun KnhMpExtension.jvmTarget(target: KnhMpTarget, minecraftVersion: String?): Int =
    maxOf(
        sourceSets.effectiveJvmTarget(
            target.variantSourceSet(minecraftVersion),
            target.bytecodeMinimum,
        ),
        effectiveConfiguration(target, minecraftVersion).jvmTarget ?: 0,
    )

/**
 * Oldest declared Kotlin API level across every target and variant; shared code is compiled against
 * it.
 */
internal fun KnhMpExtension.minimumKotlinApiVersion(): String? =
    targets
        .all()
        .flatMap { target ->
            (target.minecraftVersions.ifEmpty { listOf(null) }).map {
                effectiveConfiguration(target, it)
            }
        }
        .mapNotNull { it.kotlinApiVersion }
        .minWithOrNull(
            compareBy({ it.substringBefore('.').toInt() }, { it.substringAfter('.').toInt() })
        )
