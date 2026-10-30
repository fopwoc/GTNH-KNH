package io.github.fopwoc.palimpsest.inspect.history

import io.github.fopwoc.palimpsest.db.DbConfig
import io.github.fopwoc.palimpsest.db.DbLog
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.OpenResult
import io.github.fopwoc.palimpsest.db.PalimpsestDb
import java.nio.file.Path
import kotlin.io.path.CopyActionResult
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * A world's history opened from a private copy. Opening a history starts a session that writes into
 * its folder, so the original is only ever read: copied, without its lock, into a temporary
 * directory with the cache next to it, and the copy is deleted on [close]. Safe to point at the
 * world of a game that is running.
 */
@OptIn(ExperimentalPathApi::class)
class HistoryCopy private constructor(val db: PalimpsestDb, private val scratch: Path) :
    AutoCloseable {
    override fun close() {
        try {
            db.close()
        } finally {
            scratch.deleteRecursively()
        }
    }

    companion object {
        private const val LOCK = ".lock"

        /** The dimensions a history folder holds, by directory, sorted. */
        fun dimensions(history: Path): List<DimensionId> =
            history
                .resolve("dimensions")
                .takeIf { it.isDirectory() }
                ?.listDirectoryEntries()
                ?.filter { it.isDirectory() }
                ?.map { DimensionId(it.name) }
                ?.sortedBy { it.key }
                .orEmpty()

        /**
         * Copies [history], and [cache] when given so indexes need not be rebuilt, and opens the
         * copy. Fails with a readable message when the folder is not a history or cannot open.
         */
        fun open(history: Path, cache: Path?, log: DbLog): HistoryCopy {
            require(history.resolve("manifests").isDirectory()) {
                "$history is not a Palimpsest history folder (no manifests/ inside)"
            }
            val scratch = createTempDirectory("palimpsest-inspect-")
            return runCatching { openCopy(history, cache, log, scratch) }
                .onFailure { scratch.deleteRecursively() }
                .getOrThrow()
        }

        private fun openCopy(history: Path, cache: Path?, log: DbLog, scratch: Path): HistoryCopy {
            val world = scratch.resolve("history")
            history.copyToRecursively(
                world,
                followLinks = false,
                copyAction = { source, target ->
                    if (source.name != LOCK) source.copyToIgnoringExistingDirectory(target, false)
                    CopyActionResult.CONTINUE
                },
            )
            val copiedCache = scratch.resolve("cache")
            if (cache != null && cache.isDirectory())
                cache.copyToRecursively(copiedCache, followLinks = false, overwrite = false)
            else copiedCache.createDirectories()
            val config = DbConfig(copiedCache, NameKinds, log)
            return when (val result = PalimpsestDb.open(world, config)) {
                is OpenResult.Opened -> HistoryCopy(result.db, scratch)
                is OpenResult.Incompatible ->
                    error(
                        "$history is storage generation ${result.generation}, not ${PalimpsestDb.GENERATION}"
                    )
                is OpenResult.SyncIncomplete ->
                    error("$history is missing files its manifest names: ${result.missing}")
                is OpenResult.Diverged ->
                    error("$history has ${result.branches.size} diverged branches")
                is OpenResult.Locked -> error("The copy of $history is locked by ${result.holder}")
            }
        }
    }
}
