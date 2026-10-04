package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.codec.CorruptDataException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

/**
 * Every manifest in a world folder and the heads among them: manifests no other one names as
 * parent. One head is a world; several mean computers continued the same history apart.
 */
internal class ManifestGraph(val manifests: List<Manifest>, val unreadable: List<Path>) {
    val heads: List<Manifest>
        get() {
            val parents = manifests.mapNotNull { it.parent }.toSet()
            return manifests.filter { it.session !in parents }.sortedBy { it.session.number }
        }

    companion object {
        fun read(layout: WorldLayout): ManifestGraph {
            if (!Files.isDirectory(layout.manifests)) return ManifestGraph(emptyList(), emptyList())
            val manifests = ArrayList<Manifest>()
            val unreadable = ArrayList<Path>()
            for (path in layout.manifests.listDirectoryEntries("*.mf")) {
                try {
                    manifests += Manifest.read(path)
                } catch (_: CorruptDataException) {
                    unreadable.add(path)
                } catch (_: IOException) {
                    unreadable.add(path)
                }
            }
            return ManifestGraph(manifests, unreadable)
        }
    }
}
