package io.github.fopwoc.knhmp

import org.gradle.api.Project

/**
 * What an including mod needs from a library: the oldest runtime it was built for. Carried as plain
 * extra properties rather than the extension object, so a module and a library loaded by different
 * plugin classloaders still read each other.
 */
internal data class KnhMpLibraryBaseline(val jvmTarget: Int, val apiVersion: String?) {
    fun publish(project: Project) {
        project.extensions.extraProperties[JVM_TARGET] = jvmTarget
        project.extensions.extraProperties[API_VERSION] = apiVersion.orEmpty()
    }

    companion object {
        private const val JVM_TARGET = "knhmp.library.jvmTarget"
        private const val API_VERSION = "knhmp.library.apiVersion"

        /** The published baseline of an evaluated project; null when it is not a KnhMP library. */
        fun of(project: Project): KnhMpLibraryBaseline? {
            val properties = project.extensions.extraProperties
            if (!properties.has(JVM_TARGET)) return null
            return KnhMpLibraryBaseline(
                properties[JVM_TARGET].toString().toInt(),
                properties[API_VERSION].toString().ifEmpty { null },
            )
        }
    }
}
