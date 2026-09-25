package io.github.fopwoc.knhmp

import org.gradle.api.Project

/**
 * NeoForge loads mods as Java modules, and a module cannot contain a package with a Java keyword
 * segment (`...component.native`); its classes are simply missing at runtime. Kotlin accepts such
 * names, so reject them when the module is configured instead.
 */
internal fun Project.verifyPackageNames(extension: KnhMpExtension) {
    val sources =
        extension.sourceSets.names().flatMap { name ->
            listOf("kotlin", "java").map { projectDir.resolve("src/$name/$it") }
        }
    val offenders =
        sources
            .filter { it.isDirectory }
            .flatMap { root ->
                root
                    .walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                    .toList()
            }
            .mapNotNull { file ->
                val packageName =
                    file.useLines { lines ->
                        lines.firstNotNullOfOrNull { PACKAGE.find(it)?.groupValues?.get(1) }
                    } ?: return@mapNotNull null
                val keywords =
                    packageName.split('.').map { it.trim('`') }.filter { it in JAVA_KEYWORDS }
                if (keywords.isEmpty()) null
                else
                    "${file.relativeTo(projectDir)}: package $packageName uses ${keywords.joinToString()}"
            }
            .distinct()
    check(offenders.isEmpty()) {
        "Package names must not contain Java keywords; NeoForge's module system cannot load them:\n" +
            offenders.joinToString("\n")
    }
}

private val PACKAGE = Regex("""^\s*package\s+([\w.`]+)""")

private val JAVA_KEYWORDS =
    setOf(
        "abstract",
        "assert",
        "boolean",
        "break",
        "byte",
        "case",
        "catch",
        "char",
        "class",
        "const",
        "continue",
        "default",
        "do",
        "double",
        "else",
        "enum",
        "extends",
        "final",
        "finally",
        "float",
        "for",
        "goto",
        "if",
        "implements",
        "import",
        "instanceof",
        "int",
        "interface",
        "long",
        "native",
        "new",
        "package",
        "private",
        "protected",
        "public",
        "return",
        "short",
        "static",
        "strictfp",
        "super",
        "switch",
        "synchronized",
        "this",
        "throw",
        "throws",
        "transient",
        "try",
        "void",
        "volatile",
        "while",
        "true",
        "false",
        "null",
        "_",
    )
