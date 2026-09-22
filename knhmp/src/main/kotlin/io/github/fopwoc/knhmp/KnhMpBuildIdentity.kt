package io.github.fopwoc.knhmp

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Default mod version shared by every module of one repository: `VERSION` from the environment
 * (CI release tags), else `git describe`, else a snapshot. Resolved once per repository root so all
 * modules and their islands agree.
 */
internal object KnhMpBuildIdentity {
    private const val FALLBACK_VERSION = "0.1.0-SNAPSHOT"
    private val versions = ConcurrentHashMap<File, String>()

    fun version(repositoryRoot: File): String =
        versions.computeIfAbsent(repositoryRoot.canonicalFile) { root ->
            System.getenv("VERSION")?.trim()?.takeIf(String::isNotEmpty)
                ?: gitVersion(root)
                ?: FALLBACK_VERSION
        }

    private fun gitVersion(repositoryRoot: File): String? {
        val process = runCatching {
            ProcessBuilder("git", "describe", "--tags", "--always", "--dirty", "--long")
                .directory(repositoryRoot)
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return null
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        if (process.waitFor() != 0 || output.isEmpty()) return null
        // An exact tag (`1.2.3-0-g<sha>`) is just the tag.
        return output.replace(Regex("^([^-]+)-0-g[0-9a-f]+$"), "$1")
    }
}
