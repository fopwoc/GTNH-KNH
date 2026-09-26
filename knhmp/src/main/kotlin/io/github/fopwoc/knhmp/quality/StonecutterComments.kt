package io.github.fopwoc.knhmp.quality

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

internal const val STONECUTTER_COMMENTS_TASK = "stonecutterComments"

/**
 * Stonecutter reads `//?` line comments, but formatters rewrite them as `// ?`, which Stonecutter
 * silently ignores: the code then compiles for the wrong version with no error anywhere. Block
 * comments (`/*? if >=26 {*/`) survive formatting unchanged, so line-comment conditions fail here,
 * already formatted or not.
 */
internal fun Project.registerStonecutterComments(sources: QualitySources): TaskProvider<*> {
    val files = sources.files(this, "**/*.kt") + sources.files(this, "**/*.java")
    return tasks.register(STONECUTTER_COMMENTS_TASK) { task ->
        task.group = "verification"
        task.description = "Fails on Stonecutter conditions written as line comments."
        task.inputs.files(files)
        task.doLast {
            val findings =
                files.files.sorted().flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        "${file.relativeTo(rootDir)}:${index + 1}: ${line.trim()}"
                            .takeIf { LINE_CONDITION.containsMatchIn(line) }
                    }
                }
            if (findings.isNotEmpty()) {
                throw GradleException(
                    "Stonecutter conditions must be block comments, e.g. /*? if >=26 {*/ ... " +
                        "/*?} else {*/ ... /*?}*/; formatters turn //? into // ?, which " +
                        "Stonecutter ignores:\n" +
                        findings.joinToString("\n")
                )
            }
        }
    }
}

private val LINE_CONDITION = Regex("""^\s*(?:\*/)?// ?\?""")
