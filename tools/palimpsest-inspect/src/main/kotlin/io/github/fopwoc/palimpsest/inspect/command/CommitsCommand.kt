package io.github.fopwoc.palimpsest.inspect.command

import com.github.ajalt.clikt.core.Context

class CommitsCommand : HistoryCommand("commits") {
    private val dimension by dimensionOption()

    override fun help(context: Context) =
        "Every commit of a dimension: tick, time, chunks and bytes"

    override fun run() = withHistory { db ->
        val dim = db.dimension(dimension)
        dim.ready.get()
        echo("%14s  %-19s  %8s  %8s  %10s".format("tick", "time", "chunks", "sections", "bytes"))
        for (commit in dim.timeline()) echo(
            "%14d  %-19s  %8d  %8d  %10d"
                .format(
                    commit.tick.value,
                    time(commit.observedAt),
                    commit.chunksChanged,
                    commit.sectionsWritten,
                    commit.bytes,
                )
        )
    }
}
