package io.github.fopwoc.palimpsest.inspect.command

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.int
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.inspect.analysis.ChunkCensus

class ChunkCommand : HistoryCommand("chunk") {
    private val dimension by dimensionOption()
    private val x by argument(help = "Chunk x, block x / 16").int()
    private val z by argument(help = "Chunk z, block z / 16").int()

    override fun help(context: Context) =
        "Every distinct version of one chunk: blocks per section and the most common blocks"

    override fun run() = withHistory { db ->
        val versions = ChunkCensus(db.dimension(dimension), db.vocabulary).versions(ChunkPos(x, z))
        if (versions.isEmpty()) echo("Chunk $x, $z was never seen in ${dimension.key}")
        for (version in versions) echo(
            "%14d  %s  #%08x  [%s]  %s"
                .format(
                    version.tick,
                    time(version.observedAt),
                    version.fingerprint,
                    version.sections.joinToString(" "),
                    version.common.joinToString { (name, count) -> "$count× $name" },
                )
        )
    }
}
