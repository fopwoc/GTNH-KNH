package io.github.fopwoc.palimpsest.inspect.command

import com.github.ajalt.clikt.core.Context
import io.github.fopwoc.palimpsest.inspect.history.HistoryCopy

class DimensionsCommand : HistoryCommand("dimensions") {
    override fun help(context: Context) = "The dimensions a history holds, with their commits"

    override fun run() = withHistory { db ->
        for (id in HistoryCopy.dimensions(history)) {
            val dimension = db.dimension(id)
            dimension.ready.get()
            val timeline = dimension.timeline()
            val span =
                if (timeline.isEmpty()) ""
                else
                    " from ${time(timeline.first().observedAt)} to ${time(timeline.last().observedAt)}"
            echo("${id.key}: ${timeline.size} commits$span")
        }
    }
}
