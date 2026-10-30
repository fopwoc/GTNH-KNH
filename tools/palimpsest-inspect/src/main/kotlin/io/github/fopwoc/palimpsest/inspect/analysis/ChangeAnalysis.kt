package io.github.fopwoc.palimpsest.inspect.analysis

import io.github.fopwoc.palimpsest.db.BlockId
import io.github.fopwoc.palimpsest.db.BlockVocabulary
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.ChunkWindow
import io.github.fopwoc.palimpsest.db.Dimension
import io.github.fopwoc.palimpsest.db.WorldTick
import io.github.fopwoc.palimpsest.inspect.analysis.ChangeSummary.UndoneLoss

/**
 * Walks [dimension]'s commits pair by pair and compares every chunk the database says changed,
 * block by block and, when it shows from above, column by column. Reads every version it compares,
 * so it takes as long as the history is busy, not as long as it is large.
 */
class ChangeAnalysis(private val dimension: Dimension, private val vocabulary: BlockVocabulary) {
    /** A chunk losing blocks since [before], the state the losses started from. */
    private class LossRun(val before: Int, val since: Long, val observedAt: Long, var blocks: Int)

    fun run(): ChangeSummary {
        dimension.ready.get()
        val timeline = dimension.timeline()
        var changes = 0
        var appeared = 0
        var identical = 0
        var biomesOnly = 0
        var surfaceChanges = 0
        var emptied = 0
        var filled = 0
        var lostBlocks = 0L
        val chunksPerCommit = ArrayList<Int>()
        val blockPairs = HashMap<Pair<String, String>, Int>()
        val surfacePairs = HashMap<Pair<String, String>, Int>()
        val losing = HashMap<ChunkPos, LossRun>()
        val undone = ArrayList<UndoneLoss>()
        for ((index, to) in timeline.withIndex()) {
            // The first commit is compared with the moment before it: everything in it appears.
            val from = timeline.getOrNull(index - 1)?.tick ?: WorldTick(to.tick.value - 1)
            val diff = dimension.diff(from, to.tick).result.get()
            chunksPerCommit += diff.changes.size
            val before = dimension.at(from)
            val after = dimension.at(to.tick)
            for (change in diff.changes) {
                changes++
                val old = if (change.appeared) null else before.volume(change.pos).result.get()
                val new = after.volume(change.pos).result.get()
                if (old == null || new == null) {
                    appeared++
                    continue
                }
                val comparison = VolumeComparison(old, new)
                if (comparison.blocksChanged == 0)
                    if (comparison.biomesChanged) biomesOnly++ else identical++
                emptied += comparison.emptied.size
                filled += comparison.filled.size
                for ((pair, blocks) in comparison.pairs) blockPairs.merge(
                    name(pair.first) to name(pair.second),
                    blocks,
                    Int::plus,
                )

                val run = losing[change.pos]
                when {
                    comparison.onlyLosses -> {
                        lostBlocks += comparison.lost
                        if (run != null) run.blocks += comparison.lost
                        else
                            losing[change.pos] =
                                LossRun(
                                    VolumeComparison.fingerprint(old),
                                    to.tick.value,
                                    to.observedAt,
                                    comparison.lost,
                                )
                    }
                    run != null && VolumeComparison.fingerprint(new) == run.before -> {
                        undone +=
                            UndoneLoss(
                                change.pos,
                                run.blocks,
                                run.since,
                                run.observedAt,
                                to.tick.value,
                            )
                        losing.remove(change.pos)
                    }
                    // Part of what was lost coming back keeps the run open.
                    run != null && comparison.lost == 0 && comparison.replaced == 0 -> Unit
                    else -> losing.remove(change.pos)
                }

                if (change.surface) {
                    surfaceChanges++
                    val window = ChunkWindow(change.pos.x, change.pos.z, 1, 1)
                    val top = before.surface(window).result.get()
                    val now = after.surface(window).result.get()
                    for (z in change.pos.z * CHUNK until change.pos.z * CHUNK + CHUNK) for (x in
                        change.pos.x * CHUNK until change.pos.x * CHUNK + CHUNK) {
                        val a = top.block(x, z)
                        val b = now.block(x, z)
                        if (a != b) surfacePairs.merge(name(a) to name(b), 1, Int::plus)
                    }
                }
            }
        }
        return ChangeSummary(
            commits = timeline.size,
            changes = changes,
            appeared = appeared,
            identical = identical,
            biomesOnly = biomesOnly,
            surfaceChanges = surfaceChanges,
            chunksPerCommit = chunksPerCommit,
            sectionsEmptied = emptied,
            sectionsFilled = filled,
            blockPairs = blockPairs,
            surfacePairs = surfacePairs,
            lostBlocks = lostBlocks,
            undoneLosses = undone,
        )
    }

    private fun name(id: BlockId): String = vocabulary.identity(id)

    private companion object {
        const val CHUNK = 16
    }
}
