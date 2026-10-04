package io.github.fopwoc.knhmp

import org.gradle.api.artifacts.ProjectDependency

class KnhMpSourceSet internal constructor(private val name: String) {

    private val parentNames = linkedSetOf<String>()
    private val includedPaths = linkedSetOf<String>()

    var jvmTarget: Int? = null
        set(value) {
            require(value == null || value >= 8) { "jvmTarget must be at least 8: $value" }
            field = value
        }

    /** JVM target of this source set's `legacyJava` directory; null when it has none. */
    internal var legacyJavaTarget: Int? = null
        private set

    /**
     * Java sources in `src/<name>/legacyJava`, compiled apart from everything else at [jvmTarget]
     * into the same jar. Meant for a mod's entry class: an old JVM can still load it and refuse to
     * start with a clear message, where the modern bytecode of the rest would only crash. The
     * tokens `@MOD_ID@`, `@MOD_NAME@`, `@MOD_VERSION@` and `@MOD_GROUP@` are replaced in them, and
     * they compile against the node's main classpath but cannot see its other sources.
     */
    fun legacyJava(jvmTarget: Int) {
        require(jvmTarget >= 8) { "legacyJava target must be at least 8: $jvmTarget" }
        legacyJavaTarget = jvmTarget
    }

    fun dependsOn(vararg sourceSets: KnhMpSourceSet) {
        sourceSets.forEach { sourceSet ->
            require(sourceSet !== this) { "$name cannot depend on itself" }
            parentNames += sourceSet.name
        }
    }

    /**
     * Compiles a KnhMP library's sources (`io.github.fopwoc.knhmp.library`) as part of this source
     * set: every island mounts them next to its own and builds them with its own toolchain and
     * stdlib, and the IDE sees the library as a project dependency. Use only the library's public
     * API; `internal` resolves differently in the two.
     */
    fun include(vararg libraries: ProjectDependency) =
        include(*libraries.map { it.path }.toTypedArray())

    /** Path form of [include], e.g. `":palimpsest-db"`. */
    fun include(vararg paths: String) {
        paths.forEach { path ->
            require(path.startsWith(":")) {
                "KnhMP libraries are included by project path, got $path"
            }
            includedPaths += path
        }
    }

    internal fun name(): String = name

    internal fun parents(): Set<String> = parentNames.toSet()

    internal fun includes(): Set<String> = includedPaths.toSet()
}
