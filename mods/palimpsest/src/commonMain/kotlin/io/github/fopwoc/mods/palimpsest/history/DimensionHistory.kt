package io.github.fopwoc.mods.palimpsest.history

import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.palimpsest.db.BlockId
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.Commit
import io.github.fopwoc.palimpsest.db.Dimension
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.WorldTick
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * The history of the dimension the client is in. The scanner stages 3D snapshots of loaded chunks
 * here; every commit interval they become one moment of history. Block ids of the database are
 * drawn with the colours of the map's [blocks] table, matched by identity.
 */
class DimensionHistory(
    private val world: WorldHistory,
    id: DimensionId,
    private val blocks: BlockTable,
    classify: (String) -> BlockClass?,
    private val interval: () -> Duration,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val dimension: Dimension = world.db.dimension(id)

    /** History block id → [blocks] id; grown on the game thread, read by page builders. */
    @Volatile private var drawn = IntArray(0)
    private var lastCommit = clock()

    init {
        // Blocks of earlier sessions this table has not seen yet get guessed colours from their
        // static look, until the live scan meets them.
        for (raw in 1 until world.db.vocabulary.size) {
            val identity = world.db.vocabulary.identity(BlockId(raw))
            remember(
                raw,
                blocks.idOf(identity).takeIf { it != 0 }
                    ?: classify(identity)?.let { blocks.guess(identity, it.color, it.tint) }
                    ?: 0,
            )
        }
    }

    /** The [blocks] id to draw history block [raw] with; 0 when it has no colour. */
    fun drawnId(raw: Int): Int = drawn.let { if (raw in it.indices) it[raw] else 0 }

    /**
     * The history id of a block met at a scan, recording its kind the first time; game thread.
     * Callers cache it per game block. [look] may come without a world position, so its colour is
     * only a guess; the map's own scan records the real one.
     */
    fun idOf(identity: String, look: BlockClass): BlockId {
        val id = world.db.vocabulary.id(identity)
        world.learn(identity, look.kind)
        if (drawnId(id.raw) == 0) remember(id.raw, blocks.guess(identity, look.color, look.tint))
        return id
    }

    fun stage(observation: ChunkObservation) = dimension.stage(observation)

    /**
     * Every second or so on the game thread: commits what was staged once an interval has passed.
     */
    fun tick(worldTime: Long) {
        if (clock() - lastCommit >= interval().toMillis()) commitNow(worldTime)
    }

    /**
     * Commits whatever is staged now, for leaving the dimension or an explicit save; null when
     * nothing is staged.
     */
    fun commitNow(worldTime: Long): CompletableFuture<Commit>? {
        lastCommit = clock()
        if (dimension.stagedCount == 0) return null
        return dimension.commitStaged(WorldTick(worldTime))
    }

    private fun remember(raw: Int, drawnId: Int) {
        val current = drawn
        val next =
            if (raw < current.size) current.copyOf()
            else current.copyOf(maxOf(raw + 1, current.size * 2))
        next[raw] = drawnId
        drawn = next
    }
}
