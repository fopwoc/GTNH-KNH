import java.io.File
import java.util.concurrent.ConcurrentHashMap

object BuildIdentity {
    private val versions = ConcurrentHashMap<File, String>()

    fun version(repositoryRoot: File): String =
        versions.computeIfAbsent(repositoryRoot.canonicalFile) { root ->
            System.getenv("VERSION")?.trim()?.takeIf(String::isNotEmpty)
                ?: gitVersion(root)
                ?: "0.1.0-SNAPSHOT"
        }

    private fun gitVersion(repositoryRoot: File): String? {
        val process =
            ProcessBuilder("git", "describe", "--tags", "--always", "--dirty", "--long")
                .directory(repositoryRoot)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        if (process.waitFor() != 0 || output.isEmpty()) return null

        return output.replace(Regex("^([^-]+)-0-g[0-9a-f]+$"), "\$1")
    }
}
