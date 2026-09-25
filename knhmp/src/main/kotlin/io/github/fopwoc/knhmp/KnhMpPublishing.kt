package io.github.fopwoc.knhmp

import groovy.util.Node
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Zip

/**
 * Maven publication of a module's development jars, one artifact per target and Minecraft version
 * (`knh-core-fabric-26.2`), for other developers to compile against. Each POM declares the module's
 * `api` and bundled libraries, so a consumer's IDE resolves them transitively.
 */
class KnhMpPublishing {
    /** Maven group of every artifact; required. */
    lateinit var groupId: String

    internal val repositories = LinkedHashMap<String, Any>()
    internal val poms = mutableListOf<Action<in MavenPom>>()

    /** A Maven repository to publish to: a URL or a local directory, like `maven { url = ... }`. */
    fun repository(name: String, url: Any) {
        require(name.matches(Regex("[A-Za-z][A-Za-z0-9]*"))) {
            "Repository name must be alphanumeric, got $name"
        }
        repositories[name] = url
    }

    /**
     * Extra POM metadata (licenses, developers) on top of the name, description and URLs KnhMP
     * fills.
     */
    fun pom(action: Action<in MavenPom>) {
        poms += action
    }

    internal fun isGroupSet() = ::groupId.isInitialized
}

internal const val PUBLISH_TASK = "publishMod"
internal const val BUNDLE_TASK = "mavenBundle"
private const val BUNDLE_REPOSITORY = "knhmpBundle"

internal fun Project.configurePublishing(extension: KnhMpExtension, islands: List<KnhMpIsland>) {
    val settings = extension.publishing ?: return
    check(settings.isGroupSet()) {
        "knhmp { publishing { groupId = ... } } is required to publish $path"
    }

    // maven-publish was applied by `publishing { }`. The Kotlin Multiplatform facade adds
    // publications of its own; they describe the IDE model, not a mod, so only [PUBLISH_TASK] is
    // meant to be run.
    val publishing = extensions.getByType(PublishingExtension::class.java)
    settings.repositories.forEach { (name, url) ->
        publishing.repositories.maven { repository ->
            repository.name = name
            repository.url = uri(url)
        }
    }
    val bundleDirectory = layout.buildDirectory.dir("knhmp/maven-bundle")
    publishing.repositories.maven { repository ->
        repository.name = BUNDLE_REPOSITORY
        repository.url = uri(bundleDirectory)
    }

    val publicationNames = islands.flatMap { island ->
        island.nodes.map { node ->
            val artifactId = island.archiveBaseName(node)
            val publicationName =
                "knhmp" +
                    artifactId.split(Regex("[^A-Za-z0-9]+")).joinToString("") {
                        it.replaceFirstChar(Char::uppercaseChar)
                    }
            publishing.publications.create(publicationName, MavenPublication::class.java) {
                publication ->
                publication.groupId = settings.groupId
                publication.artifactId = artifactId
                publication.version = extension.modVersion
                publication.artifact(island.devJar(node)) { artifact ->
                    artifact.extension = "jar"
                    artifact.builtBy(tasks.named(island.buildTaskName(node)))
                }
                publication.pom { pom ->
                    pom.name.set(
                        "${extension.modName} for ${island.target.name}" +
                            node.minecraftVersion?.let { " $it" }.orEmpty()
                    )
                    pom.description.set(
                        "Development jar of ${extension.modName}: compile against it, ship the loader jar."
                    )
                    extension.repositoryUrl.takeIf(String::isNotEmpty)?.let { url ->
                        pom.url.set(url)
                        pom.scm { it.url.set(url) }
                    }
                    settings.poms.forEach { it.execute(pom) }
                    pom.withXml { xml ->
                        xml.asNode().appendDependencies(this, island, node)
                    }
                }
            }
            publicationName
        }
    }
    fun publishTasks(repository: String) = publicationNames.map { publication ->
        "publish${publication.replaceFirstChar(Char::uppercaseChar)}PublicationTo${repository.replaceFirstChar(Char::uppercaseChar)}Repository"
    }

    tasks.register(PUBLISH_TASK) { task ->
        task.group = "publishing"
        task.description =
            "Publishes the development jar of every KnhMP target and variant to the declared repositories."
        task.dependsOn(settings.repositories.keys.flatMap(::publishTasks))
    }

    // This version alone, as a zip for release assets; `mavenSite` joins every release's bundle.
    val cleanBundle =
        tasks.register("cleanMavenBundle", Delete::class.java) { it.delete(bundleDirectory) }
    val bundlePublishTasks = publishTasks(BUNDLE_REPOSITORY)
    tasks.matching { it.name in bundlePublishTasks }.configureEach { it.dependsOn(cleanBundle) }
    tasks.register(BUNDLE_TASK, Zip::class.java) { zip ->
        zip.group = "publishing"
        zip.description =
            "Zips this version's development jars as a Maven repository slice, for a release asset."
        zip.dependsOn(bundlePublishTasks)
        zip.from(bundleDirectory) { it.exclude("**/maven-metadata.xml*") }
        zip.archiveFileName.set("${extension.archiveName}-maven-${extension.modVersion}.zip")
        zip.destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    }
    rootProject.registerMavenSite(extension.repositoryUrl)
}

/**
 * Libraries consumers compile against: `api` and bundled ones in compile scope, KnhMP module
 * dependencies as their published artifacts for the same target and version. Loader-provided
 * libraries and implementation details stay out.
 */
private fun Node.appendDependencies(project: Project, island: KnhMpIsland, node: KnhMpIslandNode) {
    val dependencies = appendNode("dependencies")
    val exclusions = node.configuration.exclusions
    node.configuration.externalDependencies
        .filter { it.configuration in KnhMpDependencies.API_CONFIGURATIONS }
        .forEach { dependency ->
            val parts = dependency.coordinates.split(':')
            check(parts.size in 3..4) {
                "Cannot publish ${dependency.coordinates}: expected group:name:version[:classifier]"
            }
            dependencies.appendDependency(
                parts[0],
                parts[1],
                parts[2],
                parts.getOrNull(3),
                "compile",
                exclusions,
            )
        }
    node.configuration.moduleDependencies.forEach { dependency ->
        val module = project.rootProject.project(dependency.path)
        val moduleExtension = module.extensions.getByType(KnhMpExtension::class.java)
        val modulePublishing =
            checkNotNull(moduleExtension.publishing) {
                "${project.path} publishes, so its dependency ${dependency.path} must publish too"
            }
        val artifactId =
            "${moduleExtension.archiveName}-${island.target.name}" +
                node.minecraftVersion?.let { "-$it" }.orEmpty()
        val scope =
            if (dependency.configuration in KnhMpDependencies.API_CONFIGURATIONS) "compile"
            else "runtime"
        dependencies.appendDependency(
            modulePublishing.groupId,
            artifactId,
            moduleExtension.modVersion,
            null,
            scope,
            emptyList(),
        )
    }
}

private fun Node.appendDependency(
    group: String,
    name: String,
    version: String,
    classifier: String?,
    scope: String,
    exclusions: List<KnhMpExclusion>,
) {
    val dependency = appendNode("dependency")
    dependency.appendNode("groupId", group)
    dependency.appendNode("artifactId", name)
    dependency.appendNode("version", version)
    classifier?.let { dependency.appendNode("classifier", it) }
    dependency.appendNode("scope", scope)
    if (exclusions.isEmpty()) return
    val excluded = dependency.appendNode("exclusions")
    exclusions.forEach { exclusion ->
        excluded.appendNode("exclusion").apply {
            appendNode("groupId", exclusion.group)
            appendNode("artifactId", exclusion.module ?: "*")
        }
    }
}
