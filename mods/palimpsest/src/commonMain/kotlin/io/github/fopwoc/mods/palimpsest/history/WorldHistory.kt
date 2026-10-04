package io.github.fopwoc.mods.palimpsest.history

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.palimpsest.db.BlockId
import io.github.fopwoc.palimpsest.db.BlockKind
import io.github.fopwoc.palimpsest.db.DbConfig
import io.github.fopwoc.palimpsest.db.LogLevel
import io.github.fopwoc.palimpsest.db.OpenResult
import io.github.fopwoc.palimpsest.db.PalimpsestDb
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * One world's history database, open while the client is in that world, across dimension changes.
 * Block kinds are decided on the game thread, which is the only place the game's block state may be
 * read: every identity the world already knows is classified when it opens, and each new one as the
 * scanner meets it. The database reads them from [kinds] on its own threads.
 */
class WorldHistory
private constructor(val db: PalimpsestDb, private val kinds: ConcurrentHashMap<String, BlockKind>) {
    /** Records the kind of an identity met for the first time; game thread. */
    fun learn(identity: String, kind: BlockKind) {
        kinds.putIfAbsent(identity, kind)
    }

    /** Closes in the background, so leaving a world does not stall the game. */
    fun closeAsync(): CompletableFuture<Unit> = db.closeAsync()

    companion object {
        private val logger = logger<WorldHistory>()

        /**
         * Opens [world]'s history, or null when it cannot be opened now; the map then runs live
         * only. [classify] answers for identities from earlier sessions; game thread.
         */
        fun open(world: Path, cache: Path, classify: (String) -> BlockClass?): WorldHistory? {
            val kinds = ConcurrentHashMap<String, BlockKind>()
            val cores = Runtime.getRuntime().availableProcessors()
            val config =
                DbConfig(
                    cacheDirectory = cache,
                    blockKinds = { identity -> kinds[identity] ?: BlockKind.SOLID },
                    log = { level, tag, message, error ->
                        val text = "[$tag] $message"
                        when (level) {
                            LogLevel.DEBUG -> logger.debug(text)
                            LogLevel.INFO -> logger.info(text)
                            LogLevel.WARN -> logger.warn(text, error)
                            LogLevel.ERROR -> logger.error(text, error)
                        }
                    },
                    // Leave the game its own cores: history is background work.
                    interactiveThreads = maxOf(1, cores / 2),
                    backgroundThreads = maxOf(1, cores / 2),
                )
            val db =
                when (val result = PalimpsestDb.open(world, config)) {
                    is OpenResult.Opened -> result.db
                    is OpenResult.Locked ->
                        return null.also {
                            logger.warn("History at {} is open elsewhere: {}", world, result.holder)
                        }
                    is OpenResult.SyncIncomplete ->
                        return null.also {
                            logger.warn("History at {} is still syncing: {}", world, result.missing)
                        }
                    is OpenResult.Diverged ->
                        return null.also {
                            logger.warn(
                                "History at {} was continued on two computers: {}",
                                world,
                                result.branches.map { it.session },
                            )
                        }
                    is OpenResult.Incompatible ->
                        return null.also {
                            logger.warn(
                                "History at {} is of storage generation {}",
                                world,
                                result.generation,
                            )
                        }
                }
            for (raw in 1 until db.vocabulary.size) {
                val identity = db.vocabulary.identity(BlockId(raw))
                kinds[identity] = classify(identity)?.kind ?: BlockKind.SOLID
            }
            logger.info("History at {}: {} known blocks", world, db.vocabulary.size)
            return WorldHistory(db, kinds)
        }
    }
}
