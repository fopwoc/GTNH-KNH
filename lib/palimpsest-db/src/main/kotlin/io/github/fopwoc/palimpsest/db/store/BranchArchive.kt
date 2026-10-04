package io.github.fopwoc.palimpsest.db.store

import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Settles a diverged world on one branch: every other head's manifest and the files only it names
 * move to `abandoned/<session>/` under their own relative paths, kept for whoever wants them back.
 */
internal object BranchArchive {
    fun keep(layout: WorldLayout, graph: ManifestGraph, kept: Manifest) {
        val keptFiles = kept.files(layout).map { it.first }.toSet()
        for (head in graph.heads) {
            if (head.session == kept.session) continue
            val target = layout.abandoned.resolve(head.session.toString())
            val moving =
                head
                    .files(layout)
                    .map { it.first }
                    .filter { it !in keptFiles && Files.exists(it) } +
                    listOf(layout.manifest(head.session))
            for (file in moving) {
                val destination = target.resolve(layout.relative(file))
                Files.createDirectories(destination.parent)
                Files.move(file, destination, REPLACE_EXISTING)
            }
        }
    }
}
