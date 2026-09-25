package io.github.fopwoc.knhmp

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Build identity shared by every module of one repository, resolved once per repository root so all
 * modules and their islands agree: the default mod version (`VERSION` from the environment for CI
 * release tags, else `git describe`, else a snapshot) and the repository's web URL (from `origin`).
 */
internal object KnhMpBuildIdentity {
    private const val FALLBACK_VERSION = "0.1.0-SNAPSHOT"
    private val versions = ConcurrentHashMap<File, String>()
    private val repositoryUrls = ConcurrentHashMap<File, String>()

    fun version(repositoryRoot: File): String =
        versions.computeIfAbsent(repositoryRoot.canonicalFile) { root ->
            System.getenv("VERSION")?.trim()?.takeIf(String::isNotEmpty)
                ?: gitVersion(root)
                ?: FALLBACK_VERSION
        }

    /** The web page of the `origin` remote, or an empty string when there is none. */
    fun repositoryUrl(repositoryRoot: File): String =
        repositoryUrls.computeIfAbsent(repositoryRoot.canonicalFile) { root ->
            git(root, "remote", "get-url", "origin")?.let(::webUrl).orEmpty()
        }

    /**
     * The https page of a Git remote: `git@host:owner/repo.git`, `ssh://git@host/owner/repo.git` and
     * `https://host/owner/repo.git` all become `https://host/owner/repo`; anything else is null.
     */
    fun webUrl(remote: String): String? {
        val (host, path) = SCP_REMOTE.matchEntire(remote.trim())?.destructured
            ?: URL_REMOTE.matchEntire(remote.trim())?.destructured
            ?: return null
        return "https://$host/${path.removeSuffix("/").removeSuffix(".git")}"
    }

    private val SCP_REMOTE = Regex("""^(?:[^@/]+@)?([^:/]+):(?!//)(.+)$""")
    private val URL_REMOTE = Regex("""^(?:ssh|git|https?)://(?:[^@/]+@)?([^/:]+)(?::\d+)?/(.+)$""")

    private fun gitVersion(repositoryRoot: File): String? =
        // An exact tag (`1.2.3-0-g<sha>`) is just the tag.
        git(repositoryRoot, "describe", "--tags", "--always", "--dirty", "--long")
            ?.replace(Regex("^([^-]+)-0-g[0-9a-f]+$"), "$1")

    private fun git(repositoryRoot: File, vararg arguments: String): String? {
        val process = runCatching {
            ProcessBuilder("git", *arguments)
                .directory(repositoryRoot)
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return null
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        return output.takeIf { process.waitFor() == 0 && it.isNotEmpty() }
    }
}
