package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.DimensionId
import java.nio.file.Path

/** Where each file of a world folder lives. Everything here is truth and syncs as a whole. */
internal class WorldLayout(val root: Path) {
    val manifest: Path = root.resolve("manifest")
    val manifestDraft: Path = root.resolve("manifest.draft")
    val vocabulary: Path = root.resolve("vocabulary")
    val lock: Path = root.resolve(".lock")

    fun segments(dimension: DimensionId): Path =
        root.resolve("dimensions").resolve(dimension.key).resolve("segments")

    fun segment(dimension: DimensionId, name: String): Path = segments(dimension).resolve(name)

    /** Path of [name] relative to the world folder, as the manifest and error messages print it. */
    fun relative(path: Path): String = root.relativize(path).toString()
}
