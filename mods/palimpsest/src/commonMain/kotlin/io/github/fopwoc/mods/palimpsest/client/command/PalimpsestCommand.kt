package io.github.fopwoc.mods.palimpsest.client.command

import io.github.fopwoc.mods.framework.client.ClientCommand
import io.github.fopwoc.mods.framework.ui.compose.screen.Screens
import io.github.fopwoc.mods.palimpsest.client.gui.MapScreen
import io.github.fopwoc.mods.palimpsest.client.gui.PalimpsestScreen
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import java.nio.file.Files

object PalimpsestCommand :
    ClientCommand(
        name = "palimpsest",
        usage = "/palimpsest [bench | flush | where | block | stats]",
    ) {
    override fun run(args: List<String>): String? =
        when (args.firstOrNull()) {
            null -> {
                Screens.open(MapScreen())
                null
            }
            "bench" -> {
                Screens.open(PalimpsestScreen())
                null
            }
            "flush" -> {
                val session = MapSessions.session ?: return "No map open"
                session.map.flush()
                "Map flushed to ${session.directory.toAbsolutePath()}"
            }
            "where" -> MapSessions.session?.directory?.toAbsolutePath()?.toString() ?: "No map open"
            "block" -> MapSessions.describeBlocksBelow()
            "stats" -> stats()
            else -> usage
        }

    override fun complete(args: List<String>): List<String> =
        if (args.size == 1) listOf("bench", "flush", "where", "block", "stats") else emptyList()

    /** What the open map holds on disk and what this session has read, for sizing real play. */
    private fun stats(): String {
        val session = MapSessions.session ?: return "No map open"
        val tree = session.map.store.tree
        var sealed = 0
        var sealedBytes = 0L
        var activeBytes = 0L
        Files.list(session.map.directory).use { files ->
            for (file in files) {
                val name = file.fileName.toString()
                if (!name.endsWith(".pseg")) continue
                if (name.startsWith("active-")) activeBytes += Files.size(file)
                else {
                    sealed++
                    sealedBytes += Files.size(file)
                }
            }
        }
        return listOf(
                "${tree.roots.size} commits, latest ${tree.latestEpoch}",
                "$sealed sealed segments, ${sealedBytes / 1024} KiB sealed + ${activeBytes / 1024} KiB active",
                "${session.map.store.tilesSeen()} tiles seen this session, ${tree.contentSize} distinct full tiles on disk, ${session.blocks.size} known blocks",
                "this session: ${tree.nodesRead()} nodes read, ${tree.tilesDecoded()} tiles decoded",
            )
            .joinToString("\n")
    }
}
