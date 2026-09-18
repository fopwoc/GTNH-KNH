package io.github.fopwoc.mods.palimpsest.client.command

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientCommand
import io.github.fopwoc.mods.framework.client.ScreenOpener
import io.github.fopwoc.mods.palimpsest.client.gui.MapScreen
import io.github.fopwoc.mods.palimpsest.client.gui.PalimpsestScreen
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions

@SideOnly(Side.CLIENT)
object PalimpsestCommand :
    ClientCommand(name = "palimpsest", usage = "/palimpsest [bench | flush | where]") {
    override fun run(args: List<String>): String? =
        when (args.firstOrNull()) {
            null -> {
                ScreenOpener.open { MapScreen() }
                null
            }
            "bench" -> {
                ScreenOpener.open(::PalimpsestScreen)
                null
            }
            "flush" -> {
                val session = MapSessions.session ?: return "No map open"
                session.map.flush()
                "Map flushed to ${session.directory.toAbsolutePath()}"
            }
            "where" -> MapSessions.session?.directory?.toAbsolutePath()?.toString() ?: "No map open"
            else -> usage
        }

    override fun complete(args: List<String>): List<String> =
        if (args.size == 1) listOf("bench", "flush", "where") else emptyList()
}
