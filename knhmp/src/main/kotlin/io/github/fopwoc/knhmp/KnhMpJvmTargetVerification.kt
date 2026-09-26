package io.github.fopwoc.knhmp

import java.io.DataInputStream
import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.Project

internal fun Project.registerJarVerification(
    extension: KnhMpExtension,
    islands: List<KnhMpIsland>,
) {
    tasks.register("verifyJvmTargets") { task ->
        task.group = "verification"
        task.description =
            "Checks inherited JVM targets and class-file versions in every target jar."
        task.dependsOn("buildAll")
        task.doLast {
            verifyJvmTargetModel(extension)
            verifyTargetJars(extension, islands)
        }
    }
    tasks.register("verifySourceClosures") { task ->
        task.group = "verification"
        task.description =
            "Checks that every jar contains exactly its logical source closure and no sibling code."
        task.dependsOn("buildAll")
        task.doLast { verifySourceClosures(extension, islands) }
    }
}

private fun Project.verifyJvmTargetModel(extension: KnhMpExtension) {
    extension.targets.all().forEach { target ->
        target.sourceSets().forEach { leaf ->
            val closure = extension.sourceSets.closure(leaf)
            val sourceRequirement =
                closure
                    .mapNotNull { name -> extension.sourceSets.sourceSet(name).jvmTarget }
                    .maxOrNull() ?: target.bytecodeMinimum
            val expected = maxOf(sourceRequirement, target.bytecodeMinimum)
            val effective = extension.sourceSets.effectiveJvmTarget(leaf, target.bytecodeMinimum)
            check(effective == expected) {
                "$leaf effective JVM target $effective does not equal max($sourceRequirement, ${target.bytecodeMinimum})"
            }
        }
    }

    extension.sourceSets.all().forEach { sourceSet ->
        val ownTarget = sourceSet.jvmTarget ?: return@forEach
        val inheritedTarget =
            extension.sourceSets
                .closure(sourceSet.name())
                .asSequence()
                .filterNot { it == sourceSet.name() }
                .mapNotNull { name -> extension.sourceSets.sourceSet(name).jvmTarget }
                .maxOrNull() ?: return@forEach
        if (ownTarget < inheritedTarget) {
            val effective = extension.sourceSets.effectiveJvmTarget(sourceSet.name(), 8)
            check(effective == inheritedTarget) {
                "${sourceSet.name()} lowered inherited JVM target $inheritedTarget to $effective"
            }
            logger.lifecycle(
                "{} declares JVM target {} but inherits {}; effective target remains {}",
                sourceSet.name(),
                ownTarget,
                inheritedTarget,
                effective,
            )
        }
    }
}

/**
 * Top-level class paths (`a/b/C`) compiled from the closure's `legacyJava` sources, with their
 * target.
 */
private fun Project.legacyJavaTargets(
    extension: KnhMpExtension,
    node: KnhMpIslandNode,
): Map<String, Int> =
    extension.sourceSets
        .closure(node.sourceSet)
        .flatMap { name ->
            val target =
                extension.sourceSets.sourceSet(name).legacyJavaTarget ?: return@flatMap emptyList()
            val root = legacyJavaRoot(name)
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .map { it.relativeTo(root).invariantSeparatorsPath.removeSuffix(".java") to target }
                .toList()
        }
        .toMap()

private fun Project.collectedJar(island: KnhMpIsland, node: KnhMpIslandNode): File =
    layout.buildDirectory.file("libs/${island.jar(node).name}").get().asFile

private fun Project.verifyTargetJars(extension: KnhMpExtension, islands: List<KnhMpIsland>) {
    val versionsByJar = islands.flatMap { island ->
        island.nodes.map { node ->
            val expectedMajor = island.jvmTarget(node) + CLASS_MAJOR_OFFSET
            val jar = collectedJar(island, node)
            val ownerPath = extension.modGroup.replace('.', '/') + "/"
            val classVersions = jar.classVersions().filterKeys { it.startsWith(ownerPath) }
            check(classVersions.isNotEmpty()) { "$jar contains no class files" }
            val legacyTargets = legacyJavaTargets(extension, node)
            classVersions.forEach { (className, actualMajor) ->
                val topLevel = className.removeSuffix(".class").substringBefore('$')
                val requiredMajor =
                    legacyTargets[topLevel]?.plus(CLASS_MAJOR_OFFSET) ?: expectedMajor
                check(actualMajor == requiredMajor) {
                    "$className in ${jar.name} has class-file major $actualMajor; expected $requiredMajor"
                }
            }
            logger.lifecycle(
                "{} JVM target {} -> class-file major {}",
                jar.name,
                expectedMajor - CLASS_MAJOR_OFFSET,
                expectedMajor,
            )
            jar.name to classVersions
        }
    }

    val sharedClasses =
        versionsByJar
            .map { (_, classVersions) -> classVersions.keys }
            .reduce(Set<String>::intersect)
    check(sharedClasses.isNotEmpty()) {
        "Target jars have no shared class produced from the canonical source closure"
    }
}

/**
 * Every logical source set is identified by the packages (directories holding Kotlin files) under
 * its source root. A jar must contain classes from each package of its closure and none from any
 * package that belongs only to source sets outside the closure.
 */
private fun Project.verifySourceClosures(extension: KnhMpExtension, islands: List<KnhMpIsland>) {
    val packagesBySourceSet =
        extension.sourceSets.names().associateWith { name ->
            listOf(projectDir.resolve("src/$name/kotlin"), javaSourceRoot(name))
                .flatMap { root ->
                    root
                        .walkTopDown()
                        .filter { it.isFile && it.extension in setOf("kt", "java") }
                        .map {
                            it.parentFile.relativeTo(root).path.replace(File.separatorChar, '/')
                        }
                        .toList()
                }
                .toSet()
        }
    islands.forEach { island ->
        island.nodes.forEach { node ->
            val closure = extension.sourceSets.closure(node.sourceSet).toSet()
            val expected = closure.flatMap { packagesBySourceSet.getValue(it) }.toSet()
            val forbidden =
                (packagesBySourceSet.keys - closure)
                    .flatMap { packagesBySourceSet.getValue(it) }
                    .toSet() - expected
            val jar = collectedJar(island, node)
            val packagesInJar =
                jar.classVersions().keys.map { it.substringBeforeLast('/', "") }.toSet()
            expected.forEach { pkg ->
                check(pkg in packagesInJar) {
                    "${jar.name} lacks classes of package $pkg from ${node.sourceSet}'s closure"
                }
            }
            forbidden.forEach { pkg ->
                check(pkg !in packagesInJar) {
                    "${jar.name} leaks classes of package $pkg outside its closure $closure"
                }
            }
            logger.lifecycle("{} <- {}", jar.name, closure.joinToString(" + "))
        }
    }
}

private fun File.classVersions(): Map<String, Int> =
    ZipFile(this).use { archive ->
        archive
            .entries()
            .asSequence()
            .filter { !it.isDirectory && it.name.endsWith(".class") }
            .associate { entry ->
                val major =
                    DataInputStream(archive.getInputStream(entry)).use { input ->
                        check(input.readInt() == CLASS_FILE_MAGIC) {
                            "${entry.name} is not a JVM class file"
                        }
                        input.readUnsignedShort()
                        input.readUnsignedShort()
                    }
                entry.name to major
            }
    }

private const val CLASS_FILE_MAGIC = 0xCAFEBABE.toInt()
private const val CLASS_MAJOR_OFFSET = 44
