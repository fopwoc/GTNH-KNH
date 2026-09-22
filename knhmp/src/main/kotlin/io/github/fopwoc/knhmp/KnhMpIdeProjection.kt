package io.github.fopwoc.knhmp

import groovy.json.JsonSlurper
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.ide.IdeDependencyResolver
import org.jetbrains.kotlin.gradle.plugin.ide.IdeMultiplatformImport
import org.jetbrains.kotlin.gradle.plugin.ide.dependencyResolvers.IdeBinaryDependencyResolver

/**
 * Presents the logical source graph to IntelliJ as a Kotlin Multiplatform facade. Binary
 * dependencies of each leaf come from its island's exported classpath file; islands are never
 * imported. The facade is one `ide` JVM target with one compilation per leaf source set (each
 * mirrors an island: its own platform classpath, and leaves may declare the same class names) and a
 * `main` compilation holding only the shared source sets, so shared code is compiled without any
 * platform API on the classpath. KGP allows a single JVM target per project, hence compilations.
 */
internal fun Project.configureIdeProjection(extension: KnhMpExtension, islands: List<KnhMpIsland>) {
    val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
    kotlin.jvmToolchain(extension.javaToolchain)

    extension.sourceSets.names().forEach { name ->
        kotlin.sourceSets.maybeCreate(name).apply {
            // Java roots (mixins) are listed as Kotlin roots: kotlinc resolves them and IntelliJ marks them as sources.
            this.kotlin.setSrcDirs(kotlinSourceRoots(extension, name) + javaSourceRoot(name))
            resources.setSrcDirs(listOf(projectDir.resolve("src/$name/resources")))
        }
    }
    extension.sourceSets.all().forEach { logicalSourceSet ->
        val sourceSet = kotlin.sourceSets.getByName(logicalSourceSet.name())
        logicalSourceSet.parents().forEach { parent -> sourceSet.dependsOn(kotlin.sourceSets.getByName(parent)) }
    }

    islands.forEach { island ->
        kotlin.sourceSets.getByName(island.sourceSet).dependencies { implementation(island.ideClasspath()) }
    }
    // Module dependencies become KMP project dependencies so shared API is source-navigable and
    // shared code compiles: module scope -> root shared source sets, target/variant scope -> leaves.
    // The other module's `ide` main compilation carries only its shared source sets.
    // Module-scope dependencies are what the shared source sets compile against in the facade, with
    // KnhMP's api/implementation mapped onto KMP's so consumers see this module's API transitively.
    // Leaves take externals from their island export; module dependencies of a target/variant scope
    // still become project dependencies of that leaf.
    val leaves = extension.targets.all().flatMap { it.sourceSets() }.toSet()
    val roots = extension.sourceSets.all().filter { it.parents().isEmpty() && it.name() !in leaves }.map { it.name() }
    fun org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler.declare(dependency: KnhMpDependencyDeclaration) {
        val notation: Any = when (dependency) {
            is KnhMpDependencyDeclaration.External -> dependency.coordinates
            is KnhMpDependencyDeclaration.Module -> project(dependency.path)
        }
        when (dependency.configuration) {
            KnhMpDependencies.API_CONFIGURATION, KnhMpDependencies.BUNDLE_CONFIGURATION -> api(notation)
            "compileOnly" -> compileOnly(notation)
            "runtimeOnly" -> runtimeOnly(notation)
            KnhMpDependencies.TEST_CONFIGURATION, KnhMpDependencies.NEOFORGE_CONFIGURATION -> Unit
            else -> implementation(notation)
        }
    }
    extension.common.dependencies.resolve()
        .filterNot { it is KnhMpDependencyDeclaration.External && it.configuration !in setOf("api", "bundle", "implementation", "compileOnly", "runtimeOnly") }
        .forEach { dependency -> roots.forEach { root -> kotlin.sourceSets.getByName(root).dependencies { declare(dependency) } } }
    extension.targets.all().forEach { target ->
        target.sourceSets().forEach { leaf ->
            val scopes = listOf(target) + target.minecraftVersions.filter { target.variantSourceSet(it) == leaf }.mapNotNull(target::variantScope)
            scopes.flatMap { it.dependencies.resolve() }.filterIsInstance<KnhMpDependencyDeclaration.Module>().distinctBy { it.path }
                .forEach { dependency -> kotlin.sourceSets.getByName(leaf).dependencies { declare(dependency) } }
        }
    }

    val ideTarget = kotlin.jvm("ide")
    val carrierJvmTarget = extension.targets.all().maxOf { target ->
        target.sourceSets().maxOf { extension.sourceSets.effectiveJvmTarget(it, target.bytecodeMinimum) }
    }
    ideTarget.compilerOptions.jvmTarget.set(JvmTarget.fromTarget(carrierJvmTarget.asKotlinJvmTarget()))
    tasks.withType(org.gradle.api.tasks.compile.JavaCompile::class.java).configureEach { it.options.release.set(carrierJvmTarget) }
    // The facade's own jars are internal (module dependencies, metadata); build/libs holds island jars only.
    tasks.withType(org.gradle.api.tasks.bundling.Jar::class.java).configureEach { it.destinationDirectory.set(layout.buildDirectory.dir("facade")) }
    val carriers = ideCarriers(extension)
    // Shared code is what other modules consume, so it must not take the newest leaf's target:
    // a Java 24 consumer cannot inline Kotlin compiled for Java 25.
    val sharedJvmTarget = carriers.getValue("main").maxOfOrNull { extension.sourceSets.effectiveJvmTarget(it, MIN_JVM_TARGET) }
    if (sharedJvmTarget != null) {
        ideTarget.compilations.maybeCreate("main").compileTaskProvider.configure { task ->
            (task as org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile).compilerOptions.jvmTarget
                .set(JvmTarget.fromTarget(sharedJvmTarget.asKotlinJvmTarget()))
        }
    }
    val minimumApi = extension.minimumKotlinApiVersion()
    carriers.forEach { (carrier, sourceSets) ->
        // Shared code compiles against the oldest declared stdlib API; each leaf against its own.
        val level = if (carrier == "main") minimumApi else extension.targets.all()
            .first { target -> sourceSets.single() in target.sourceSets() }
            .let { target -> target.minecraftVersions.map { extension.effectiveConfiguration(target, it).kotlinApiVersion }.ifEmpty { listOf(extension.effectiveConfiguration(target, null).kotlinApiVersion) } }
            .filterNotNull().minByOrNull { it.substringBefore('.').toInt() * 100 + it.substringAfter('.').toInt() }
        if (level != null) {
            ideTarget.compilations.maybeCreate(carrier).compileTaskProvider.configure { task ->
                (task as org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile).compilerOptions.apply {
                    apiVersion.set(KotlinVersion.fromVersion(level))
                    languageVersion.set(KotlinVersion.fromVersion(level))
                }
            }
        }
        // KGP derives a "source set tree" from the compilation name and warns that a non-main tree
        // depends on commonMain (KotlinSourceSetTreeDependsOnMismatch). The classifier that would put
        // these compilations into the main tree is KGP-internal, so the diagnostic is suppressed by
        // id in gradle.properties instead; the edges are exactly the intended production graph.
        ideTarget.compilations.maybeCreate(carrier).defaultSourceSet.apply {
            sourceSets.forEach { dependsOn(kotlin.sourceSets.getByName(it)) }
        }
    }

    configureCommonTests(extension, kotlin)
    val platformTests = configurePlatformTests(islands, ideTarget)
    configureIdeDependencyResolver(extension, islands, carriers)
    registerIdeVerification(extension, islands, carriers.keys + platformTests)
}

/**
 * Tests exist for shared code only: everything outside the shared source sets is a thin platform
 * adapter or an entrypoint. `commonTest` compiles against the shared source sets in the module
 * build (no platform, no nested build) with kotlin-test on JUnit 5, and `check` runs it.
 */
private fun Project.configureCommonTests(extension: KnhMpExtension, kotlin: KotlinMultiplatformExtension) {
    // The `ide` test compilation is associated with `main`, which holds exactly the shared source
    // sets, so commonTest sees shared code and nothing platform-specific (KGP forbids dependsOn here).
    val commonTest = kotlin.sourceSets.maybeCreate("commonTest").apply {
        this.kotlin.setSrcDirs(listOf(projectDir.resolve("src/commonTest/kotlin")))
        resources.setSrcDirs(listOf(projectDir.resolve("src/commonTest/resources")))
        dependencies {
            implementation(kotlin("test"))
            extension.common.dependencies.resolve()
                .filter { it.configuration == KnhMpDependencies.TEST_CONFIGURATION }
                .forEach { dependency ->
                    when (dependency) {
                        is KnhMpDependencyDeclaration.External -> implementation(dependency.coordinates)
                        is KnhMpDependencyDeclaration.Module -> implementation(project(dependency.path))
                    }
                }
        }
    }
    kotlin.jvm("ide").compilations.getByName("test").defaultSourceSet.dependsOn(commonTest)
    tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach { it.useJUnitPlatform() }
    tasks.named("check").configure { it.dependsOn("ideTest") }
    if (this != rootProject) rootProject.tasks.maybeCreate("check").dependsOn("$path:check")
}

/**
 * Loader tests are separate custom compilations associated only with their matching main carrier.
 * Like islands, they see `commonTest` fixtures. They are compiled here for IDE correctness and
 * executed by the authoritative compiler island.
 */
private fun Project.configurePlatformTests(
    islands: List<KnhMpIsland>,
    ideTarget: org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget,
): Set<String> =
    islands
        .groupBy(KnhMpIsland::sourceSet)
        .mapTo(linkedSetOf()) { (mainSourceSet, sourceSetIslands) ->
            val compilationName = testSourceSetOf(mainSourceSet)
            val mainCompilation = ideTarget.compilations.getByName(mainSourceSet.removeSuffix("Main"))
            val testCompilation = ideTarget.compilations.maybeCreate(compilationName)
            testCompilation.associateWith(mainCompilation)
            val commonTest = extensions.getByType(KotlinMultiplatformExtension::class.java).sourceSets.getByName("commonTest")
            testCompilation.defaultSourceSet.apply {
                dependsOn(commonTest)
                val root = projectDir.resolve("src/$compilationName")
                kotlin.setSrcDirs(listOf(root.resolve("kotlin"), root.resolve("java")))
                resources.setSrcDirs(listOf(root.resolve("resources")))
                dependencies {
                    implementation(kotlin("test-junit5"))
                    sourceSetIslands
                        .flatMap { it.activeNode().configuration.testDependencies }
                        .distinct()
                        .forEach { dependency ->
                            when (dependency) {
                                is KnhMpDependencyDeclaration.External ->
                                    implementation(dependency.coordinates)
                                is KnhMpDependencyDeclaration.Module ->
                                    error(
                                        "Target test dependency ${dependency.path} for $mainSourceSet " +
                                            "must be declared on the matching main scope",
                                    )
                            }
                        }
                }
            }
            val level =
                sourceSetIslands
                    .mapNotNull { it.activeNode().configuration.kotlinApiVersion }
                    .minByOrNull {
                        it.substringBefore('.').toInt() * 100 + it.substringAfter('.').toInt()
                    }
            if (level != null) {
                testCompilation.compileTaskProvider.configure { task ->
                    (task as org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile).compilerOptions.apply {
                        apiVersion.set(KotlinVersion.fromVersion(level))
                        languageVersion.set(KotlinVersion.fromVersion(level))
                    }
                }
            }
            compilationName
        }

/**
 * `main` -> source sets shared by every leaf (platform-free); one compilation named after each
 * leaf -> that leaf. Intermediates shared by only some leaves (e.g. a vanilla-only layer under the
 * 26.x loaders) are compiled through those leaves' compilations and get no compilation of their own.
 */
private fun ideCarriers(extension: KnhMpExtension): Map<String, List<String>> {
    val leaves = extension.targets.all().flatMap { it.sourceSets() }.toSet()
    val closures = leaves.map { extension.sourceSets.closure(it).toSet() }
    val shared = extension.sourceSets.names().filter { name -> name !in leaves && closures.all { name in it } }.sorted()
    return linkedMapOf("main" to shared) + leaves.associateBy { it.removeSuffix("Main") }.mapValues { listOf(it.value) }
}

/** Carrier compilation whose compile classpath resolves a source set for the IDE; intermediates use the first leaf below them. */
private fun ideCarrierOf(extension: KnhMpExtension, carriers: Map<String, List<String>>): Map<String, String> {
    val leaves = extension.targets.all().flatMap { it.sourceSets() }
    val direct = carriers.flatMap { (carrier, sourceSets) -> sourceSets.map { it to carrier } }.toMap()
    return extension.sourceSets.names().associateWith { name ->
        direct[name] ?: leaves.first { name in extension.sourceSets.closure(it) }.removeSuffix("Main")
    }
}

/**
 * KGP resolves nothing for a source set shared by several compilations of one JVM target, so every
 * logical source set is resolved through the compile classpath of its carrier compilation: shared
 * sets through `main`, each leaf through its own. Those classpaths are exact by construction and
 * yield JVM artifacts for module dependencies (`framework-ide.jar`), which IntelliJ maps back to the
 * producing source sets; the metadata configurations would yield a metadata jar it cannot map.
 */
@OptIn(ExternalKotlinTargetApi::class)
private fun Project.configureIdeDependencyResolver(
    extension: KnhMpExtension,
    islands: List<KnhMpIsland>,
    carriers: Map<String, List<String>>,
) {
    val carrierOf = ideCarrierOf(extension, carriers)
    val binaryResolver = IdeBinaryDependencyResolver(
        artifactResolutionStrategy =
            IdeBinaryDependencyResolver.ArtifactResolutionStrategy.ResolvableConfiguration(
                configurationSelector = { sourceSet ->
                    val carrier = carrierOf.getValue(sourceSet.name)
                    val compilation = if (carrier == "main") "" else carrier.replaceFirstChar { it.titlecase(Locale.ROOT) }
                    configurations.getByName("ide${compilation}CompileClasspath")
                },
            ),
    )
    // The island export tasks run before KGP resolves IDE dependencies, so the classpath files exist.
    val buildDependencies = islands.map { "$path:${it.ideClasspathTask}" }
    val resolver = object : IdeDependencyResolver, IdeDependencyResolver.WithBuildDependencies {
        override fun resolve(sourceSet: KotlinSourceSet): Set<IdeaKotlinDependency> =
            binaryResolver.resolve(sourceSet)

        override fun dependencies(project: Project): Iterable<Any> = buildDependencies
    }

    IdeMultiplatformImport.instance(this).registerDependencyResolver(
        resolver = resolver,
        constraint = IdeMultiplatformImport.SourceSetConstraint {
            it.name in extension.sourceSets.names()
        },
        phase = IdeMultiplatformImport.DependencyResolutionPhase.BinaryDependencyResolution,
        priority = IdeMultiplatformImport.Priority.veryHigh,
    )
}

private fun Project.registerIdeVerification(
    extension: KnhMpExtension,
    islands: List<KnhMpIsland>,
    compilations: Set<String>,
) {
    val module = this
    tasks.register("verifyIdeFacade") { task ->
        task.group = "verification"
        task.description = "Compiles every IDE main and loader-test compilation and checks the facade source graph."
        task.dependsOn(compilations.map { compilation ->
            if (compilation == "main") "compileKotlinIde"
            else "compile${compilation.replaceFirstChar { it.titlecase(Locale.ROOT) }}KotlinIde"
        })
        task.doLast {
            val kotlin = module.extensions.getByType(KotlinMultiplatformExtension::class.java)
            extension.sourceSets.all().forEach { logicalSourceSet ->
                val actual = kotlin.sourceSets.getByName(logicalSourceSet.name())
                    .dependsOn
                    .mapTo(linkedSetOf()) { it.name }
                check(actual == logicalSourceSet.parents()) {
                    "${logicalSourceSet.name()} depends on $actual instead of ${logicalSourceSet.parents()}"
                }
            }
        }
    }

    tasks.register("verifyIdeDependencyModel") { task ->
        task.group = "verification"
        task.description = "Checks the source graph and isolated classpaths exported to IntelliJ by KGP."
        task.dependsOn("resolveIdeDependencies")
        task.doLast { verifyExportedIdeModel(extension, islands) }
    }
}

private fun Project.verifyExportedIdeModel(extension: KnhMpExtension, islands: List<KnhMpIsland>) {
    val modelDirectory = layout.buildDirectory.dir("ide/dependencies/json").get().asFile

    @Suppress("UNCHECKED_CAST")
    fun entries(name: String): List<Map<String, Any?>> =
        JsonSlurper().parse(modelDirectory.resolve("$name.json")) as List<Map<String, Any?>>

    fun exportedClasspath(name: String): Set<File> = entries(name)
        .flatMap { (it["classpath"] as? List<*>).orEmpty() }
        .map { it.toString() }
        .mapTo(linkedSetOf()) { path -> resolveExportedPath(path) }

    fun exportedParents(name: String): Set<String> = entries(name)
        .filter { it["type"] == "DependsOn" }
        .mapNotNull { it["coordinates"]?.toString()?.substringAfterLast('/') }
        .toSet()

    extension.sourceSets.names().forEach { name ->
        val expected = extension.sourceSets.closure(name).toSet() - name
        val actual = exportedParents(name)
        check(actual == expected) { "$name exports parents $actual instead of $expected" }
    }

    val modernMinecraft = "net/minecraft/SharedConstants.class"
    val legacyMinecraft = "net/minecraft/init/Blocks.class"
    val fabric = "net/fabricmc/api/ModInitializer.class"
    val neoforge = "net/neoforged/fml/common/Mod.class"
    val legacyFml = "cpw/mods/fml/common/Mod.class"
    val legacyForge = "net/minecraftforge/common/MinecraftForge.class"
    val platformClasses = listOf(modernMinecraft, legacyMinecraft, fabric, neoforge, legacyFml, legacyForge)

    val commonClasspath = exportedClasspath("commonMain")
    check(platformClasses.none { commonClasspath.containsClass(it) }) { "commonMain exports a Minecraft or loader API" }

    islands.forEach { island ->
        val classpath = exportedClasspath(island.sourceSet)
        val label = "${island.sourceSet} (${island.name})"
        when (island.target.name) {
            "gtnh" -> {
                check(classpath.containsClass(legacyMinecraft)) { "$label does not export Minecraft 1.7.10" }
                check(classpath.containsClass(legacyFml)) { "$label does not export FML 1.7.10" }
                check(classpath.containsClass(legacyForge)) { "$label does not export Forge 1.7.10" }
                check(!classpath.containsClass(fabric) && !classpath.containsClass(neoforge)) { "$label exports a modern loader API" }
            }
            "fabric" -> {
                check(classpath.containsClass(modernMinecraft) && classpath.containsClass(fabric)) {
                    "$label does not export Minecraft and Fabric"
                }
                check(!classpath.containsClass(neoforge) && !classpath.containsClass(legacyFml) && !classpath.containsClass(legacyForge)) {
                    "$label exports a sibling loader API"
                }
            }
            "neoforge" -> {
                check(classpath.containsClass(modernMinecraft) && classpath.containsClass(neoforge)) {
                    "$label does not export modern Minecraft and NeoForge"
                }
                check(!classpath.containsClass(fabric) && !classpath.containsClass(legacyFml) && !classpath.containsClass(legacyForge)) {
                    "$label exports a sibling loader API"
                }
            }
        }
    }
}

private fun Project.resolveExportedPath(path: String): File {
    val candidate = File(path)
    if (candidate.isAbsolute) return candidate
    return listOf(file(path), rootProject.file(path)).firstOrNull(File::exists) ?: file(path)
}

private fun Set<File>.containsClass(path: String): Boolean = any { file ->
    when {
        file.isDirectory -> file.resolve(path).isFile
        file.extension == "jar" -> runCatching {
            ZipFile(file).use { it.getEntry(path) != null }
        }.getOrDefault(false)
        else -> false
    }
}

private const val MIN_JVM_TARGET = 8
