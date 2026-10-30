package io.github.fopwoc.palimpsest.inspect.command

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.github.fopwoc.palimpsest.inspect.analysis.ChangeAnalysis
import io.github.fopwoc.palimpsest.inspect.analysis.ChangeSummary

class ChangesCommand : HistoryCommand("changes") {
    private val dimension by dimensionOption()
    private val top by option(help = "Rows per list").int().default(TOP)

    override fun help(context: Context) =
        "What a dimension's commits recorded as changed, sorted into kinds, to find changes that " +
            "are not real"

    override fun run() = withHistory { db ->
        val summary = ChangeAnalysis(db.dimension(dimension), db.vocabulary).run()
        report(summary)
    }

    private fun report(s: ChangeSummary) {
        echo(
            "${dimension.key}: ${s.commits} commits, ${s.changes} chunk changes, ${s.appeared} first seen"
        )
        if (s.chunksPerCommit.isNotEmpty())
            echo(
                "chunks per commit: median ${s.chunksPerCommit.sorted()[s.chunksPerCommit.size / 2]}, " +
                    "max ${s.chunksPerCommit.max()}"
            )
        echo("changed with nothing different: ${s.identical}")
        echo("only biomes changed: ${s.biomesOnly}")
        echo("visible from above: ${s.surfaceChanges}")
        echo("whole sections emptied ${s.sectionsEmptied}, filled ${s.sectionsFilled}")
        echo(
            "\nlosses later undone exactly: ${s.undoneLosses.size}, " +
                "${s.undoneLosses.sumOf { it.blocks }} of ${s.lostBlocks} blocks lost"
        )
        for (loss in s.undoneLosses.take(top)) echo(
            "  chunk ${loss.pos.x}, ${loss.pos.z}: ${loss.blocks} blocks gone at ${loss.lostAt} " +
                "(${time(loss.lostObservedAt)}), back ${loss.backAt - loss.lostAt} ticks later"
        )
        list("blocks that changed into another (from -> to: blocks)", s.blockPairs)
        list("identities that kept their block (metadata, machine details)", s.sameBlockPairs)
        list("top blocks seen from above (from -> to: columns)", s.surfacePairs)
    }

    private fun list(title: String, pairs: Map<Pair<String, String>, Int>) {
        echo("\n$title:")
        for ((pair, count) in pairs.entries.sortedByDescending { it.value }.take(top)) echo(
            "  %8d  %s -> %s".format(count, pair.first, pair.second)
        )
    }

    private companion object {
        const val TOP = 30
    }
}
