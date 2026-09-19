package io.github.fopwoc.mods.palimpsest.client.command

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientCommand
import io.github.fopwoc.mods.framework.client.ScreenOpener
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import io.github.fopwoc.mods.palimpsest.client.gui.MapScreen
import io.github.fopwoc.mods.palimpsest.client.gui.PalimpsestScreen
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import java.nio.file.Files
import net.minecraft.client.Minecraft

@SideOnly(Side.CLIENT)
object PalimpsestCommand :
    ClientCommand(name = "palimpsest", usage = "/palimpsest [bench | flush | where | block | stats]") {
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
            "block" -> describeBlockBelow()
            "stats" -> stats()
            else -> usage
        }

    override fun complete(args: List<String>): List<String> =
        if (args.size == 1) listOf("bench", "flush", "where", "block", "stats") else emptyList()

    /** The first block at or below the player's feet that the map would consider, and why. */
    private fun describeBlockBelow(): String {
        val minecraft = Minecraft.getMinecraft()
        val player = minecraft.thePlayer ?: return "No player"
        val world = minecraft.theWorld ?: return "No world"
        val x = Math.floor(player.posX).toInt()
        val z = Math.floor(player.posZ).toInt()
        var y = Math.floor(player.posY).toInt() - 1
        val lines = ArrayList<String>()
        while (y >= 0 && lines.size < 4) {
            val block = world.getBlock(x, y, z)
            if (!block.isAir(world, x, y, z)) {
                lines += "y=$y ${BlockColors.describe(block, world.getBlockMetadata(x, y, z))}"
            }
            y--
        }
        return lines.joinToString("\n").ifEmpty { "Nothing below" }
    }

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
                "${tree.contentSize} distinct full tiles, ${session.blocks.size} known blocks",
                "this session: ${tree.nodesRead()} nodes read, ${tree.tilesDecoded()} tiles decoded",
            )
            .joinToString("\n")
    }
}
