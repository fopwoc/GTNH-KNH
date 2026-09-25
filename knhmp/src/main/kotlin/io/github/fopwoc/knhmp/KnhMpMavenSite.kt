package io.github.fopwoc.knhmp

import groovy.json.JsonSlurper
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * A Maven repository assembled from release assets: every `*-maven-*.zip` attached to the GitHub
 * releases of [repository] (and any [extraBundles]), unpacked into one layout, with each artifact's
 * `maven-metadata.xml` regenerated from the versions present. Releases are the storage and the site
 * is a view, rebuilt whole every time, so nothing carried over can go missing; any failed request
 * fails the task rather than yield a site without some versions.
 */
@DisableCachingByDefault(because = "Reads releases from GitHub, which the build cannot track")
abstract class KnhMpMavenSite : DefaultTask() {
    /** `owner/name` of the GitHub repository whose releases hold the bundles. */
    @get:Input @get:Optional abstract val repository: Property<String>

    /** Bundles to add on top of the releases', e.g. one not yet attached to a release. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val extraBundles: ConfigurableFileCollection

    @get:OutputDirectory abstract val output: DirectoryProperty

    init {
        // The releases live outside the build; their state is never up to date.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun assemble() {
        val site =
            output.get().asFile.apply {
                deleteRecursively()
                mkdirs()
            }
        val downloads = temporaryDir.apply {
            deleteRecursively()
            mkdirs()
        }
        val bundles =
            repository.orNull?.let { releaseBundles(it, downloads) }.orEmpty() +
                extraBundles.files.filter(File::isFile)
        bundles.forEach { unzip(it, site) }
        val artifacts = site.walkTopDown().filter(::isArtifactDirectory).toList()
        artifacts.forEach { writeMetadata(site, it) }
        logger.lifecycle(
            "Maven site: {} bundles, {} artifacts, in {}",
            bundles.size,
            artifacts.size,
            site,
        )
    }

    private fun releaseBundles(repository: String, into: File): List<File> {
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        val token = System.getenv("GITHUB_TOKEN") ?: System.getenv("GH_TOKEN")
        val bundles = mutableListOf<File>()
        for (page in 1..MAX_PAGES) {
            val url = "https://api.github.com/repos/$repository/releases?per_page=100&page=$page"
            val request =
                HttpRequest.newBuilder(URI(url))
                    .header("Accept", "application/vnd.github+json")
                    .apply { token?.let { header("Authorization", "Bearer $it") } }
                    .build()
            @Suppress("UNCHECKED_CAST")
            val releases =
                JsonSlurper().parseText(client.fetch(request, url).toString(Charsets.UTF_8))
                    as List<Map<String, Any?>>
            if (releases.isEmpty()) break
            releases
                .filter { it["draft"] != true }
                .flatMap { release -> release["assets"] as List<Map<String, Any?>> }
                .filter { BUNDLE.matches(it["name"].toString()) }
                .forEach { asset ->
                    val download = asset["browser_download_url"].toString()
                    val file = into.resolve(asset["name"].toString())
                    file.writeBytes(
                        client.fetch(HttpRequest.newBuilder(URI(download)).build(), download)
                    )
                    bundles += file
                }
        }
        return bundles
    }

    private fun HttpClient.fetch(request: HttpRequest, url: String): ByteArray {
        val response = send(request, HttpResponse.BodyHandlers.ofByteArray())
        check(response.statusCode() == HTTP_OK) {
            "GET $url answered ${response.statusCode()}; refusing to build a site that may lack versions"
        }
        return response.body()
    }

    private fun unzip(bundle: File, site: File) =
        ZipFile(bundle).use { zip ->
            zip.entries()
                .asSequence()
                .filterNot { it.isDirectory }
                .forEach { entry ->
                    val target = site.resolve(entry.name).canonicalFile
                    check(target.startsWith(site.canonicalFile)) {
                        "${bundle.name} escapes the site: ${entry.name}"
                    }
                    target.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use(input::copyTo)
                    }
                }
        }

    /** A directory of version directories that hold POMs: `group/path/artifact/`. */
    private fun isArtifactDirectory(directory: File): Boolean =
        directory.isDirectory && versionsIn(directory).isNotEmpty()

    private fun versionsIn(artifact: File): List<String> =
        artifact
            .listFiles()
            .orEmpty()
            .filter { version ->
                version.isDirectory && version.listFiles().orEmpty().any { it.extension == "pom" }
            }
            .map(File::getName)

    private fun writeMetadata(site: File, artifact: File) {
        val coordinates = artifact.relativeTo(site).invariantSeparatorsPath.split('/')
        val versions = versionsIn(artifact).sortedWith(VERSION_ORDER)
        val newest = versions.last()
        val metadata = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("<metadata>")
            appendLine("  <groupId>${coordinates.dropLast(1).joinToString(".")}</groupId>")
            appendLine("  <artifactId>${coordinates.last()}</artifactId>")
            appendLine("  <versioning>")
            appendLine("    <latest>$newest</latest>")
            appendLine("    <release>$newest</release>")
            appendLine("    <versions>")
            versions.forEach { appendLine("      <version>$it</version>") }
            appendLine("    </versions>")
            appendLine(
                "    <lastUpdated>${ZonedDateTime.now(ZoneOffset.UTC).format(TIMESTAMP)}</lastUpdated>"
            )
            appendLine("  </versioning>")
            appendLine("</metadata>")
        }
            .toByteArray()
        val file = artifact.resolve("maven-metadata.xml").apply { writeBytes(metadata) }
        CHECKSUMS.forEach { (extension, algorithm) ->
            val digest = MessageDigest.getInstance(algorithm).digest(metadata)
            file
                .resolveSibling("${file.name}.$extension")
                .writeText(digest.joinToString("") { "%02x".format(it) })
        }
    }

    internal companion object {
        const val TASK = "mavenSite"
        private const val HTTP_OK = 200
        private const val MAX_PAGES = 100
        private val BUNDLE = Regex(".+-maven-.+\\.zip")
        private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        private val CHECKSUMS =
            listOf("md5" to "MD5", "sha1" to "SHA-1", "sha256" to "SHA-256", "sha512" to "SHA-512")

        /** Numeric parts compare as numbers: `2.10.0` after `2.9.1`. */
        private val VERSION_ORDER =
            Comparator<String> { left, right ->
                val a = left.split('.', '-', '+')
                val b = right.split('.', '-', '+')
                a.zip(b)
                    .map { (x, y) ->
                        val (m, n) = x.toLongOrNull() to y.toLongOrNull()
                        if (m != null && n != null) m.compareTo(n) else x.compareTo(y)
                    }
                    .firstOrNull { it != 0 } ?: a.size.compareTo(b.size)
            }

        /** `owner/name` of a `https://github.com/owner/name` page; null for any other host. */
        fun githubRepository(url: String): String? =
            Regex("^https://github\\.com/([^/]+/[^/]+?)/?$").matchEntire(url)?.groupValues?.get(1)
    }
}

/**
 * The root's `mavenSite` task, once for the build. `-PmavenSiteBundles=<dir>` adds the zips in a
 * directory, e.g. this build's own `build/distributions`, to the releases'.
 */
internal fun Project.registerMavenSite(repositoryUrl: String) {
    if (KnhMpMavenSite.TASK in tasks.names) return
    tasks.register(KnhMpMavenSite.TASK, KnhMpMavenSite::class.java) { site ->
        site.group = "publishing"
        site.description =
            "Builds a Maven repository from every GitHub release's development-jar bundles."
        KnhMpMavenSite.githubRepository(repositoryUrl)?.let(site.repository::set)
        providers.gradleProperty("mavenSiteBundles").orNull?.let { directory ->
            site.extraBundles.from(fileTree(directory) { it.include("*-maven-*.zip") })
        }
        site.output.set(layout.buildDirectory.dir("maven-site"))
    }
}
