package io.github.fopwoc.knhmp

/**
 * Maven repositories of a module's compiler islands, on top of the ones each backend needs. Modules
 * that depend on this module inherit them, since they resolve its `api` dependencies too.
 */
class KnhMpRepositories {

    private val declarations = linkedSetOf<String>()

    fun mavenCentral() {
        declarations += "mavenCentral()"
    }

    fun google() {
        declarations += "google()"
    }

    fun maven(url: String) {
        declarations += "maven(\"${url.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")}\")"
    }

    /** Kotlin DSL lines for a generated `repositories { }` block. */
    internal fun lines(): Set<String> = declarations
}
