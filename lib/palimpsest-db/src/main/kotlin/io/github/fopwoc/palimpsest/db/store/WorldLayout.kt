package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.DimensionId
import java.nio.file.Path

/**
 * Where each file of a world folder lives. Everything here is truth and syncs as a whole, and every
 * file is named after the session that wrote it, so two computers never write the same name and a
 * cloud folder never has to pick between two versions of one file.
 */
internal class WorldLayout(val root: Path) {
    val manifests: Path = root.resolve("manifests")
    val vocabulary: Path = root.resolve("vocabulary")
    val dimensions: Path = root.resolve("dimensions")
    val abandoned: Path = root.resolve("abandoned")
    val lock: Path = root.resolve(".lock")

    fun manifest(session: Manifest.Session): Path = manifests.resolve("$session.mf")

    fun vocabulary(name: String): Path = vocabulary.resolve(name)

    fun segments(dimension: DimensionId): Path =
        dimensions.resolve(dimension.key).resolve("segments")

    fun segment(dimension: DimensionId, name: String): Path = segments(dimension).resolve(name)

    /** Path relative to the world folder, as error messages and the archive use it. */
    fun relative(path: Path): String = root.relativize(path).toString()
}
