package io.github.fopwoc.mods.palimpsest.client.command

import io.github.fopwoc.mods.framework.client.ClientCommand
import io.github.fopwoc.mods.framework.ui.compose.screen.Screens
import io.github.fopwoc.mods.palimpsest.client.gui.MapScreen
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions

object PalimpsestCommand :
    ClientCommand(
        name = "palimpsest",
        usage = "/palimpsest [flush | where | block | stats]",
    ) {
    override fun run(args: List<String>): String? =
        when (args.firstOrNull()) {
            null -> {
                Screens.open(MapScreen())
                null
            }
            "flush" -> {
                val session = MapSessions.session ?: return "No map open"
                session.map.flush()
                "Committed what was staged to history"
            }
            "where" -> MapSessions.session?.directory?.toAbsolutePath()?.toString() ?: "No map open"
            "block" -> MapSessions.describeBlocksBelow()
            "stats" -> stats()
            else -> usage
        }

    override fun complete(args: List<String>): List<String> =
        if (args.size == 1) listOf("flush", "where", "block", "stats") else emptyList()

    /** How much history the open dimension holds and what the database is busy with. */
    private fun stats(): String {
        val session = MapSessions.session ?: return "No map open"
        val history = session.history ?: return "History is unavailable; the map runs live only"
        val dimension = history.dimension
        val timeline = dimension.timeline()
        return listOfNotNull(
                "${timeline.size} moments of history, ${dimension.stagedCount} chunks staged for the next",
                timeline.lastOrNull()?.let {
                    "latest at world tick ${it.tick.value}, ${it.chunksChanged} chunks changed"
                },
                "${session.map.store.tilesSeen()} tiles seen this session, ${session.blocks.size} known blocks",
                dimension.retention.let { "keeps ${it.name.lowercase()}" },
            )
            .joinToString("\n")
    }
}
