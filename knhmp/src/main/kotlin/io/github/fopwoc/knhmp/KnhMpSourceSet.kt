package io.github.fopwoc.knhmp

class KnhMpSourceSet internal constructor(private val name: String) {

    private val parentNames = linkedSetOf<String>()

    var jvmTarget: Int? = null
        set(value) {
            require(value == null || value >= 8) { "jvmTarget must be at least 8: $value" }
            field = value
        }

    fun dependsOn(vararg sourceSets: KnhMpSourceSet) {
        sourceSets.forEach { sourceSet ->
            require(sourceSet !== this) { "$name cannot depend on itself" }
            parentNames += sourceSet.name
        }
    }

    internal fun name(): String = name

    internal fun parents(): Set<String> = parentNames.toSet()
}
