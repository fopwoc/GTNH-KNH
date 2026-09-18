package io.github.fopwoc.mods.palimpsest.client.command

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientCommand
import io.github.fopwoc.mods.framework.client.ScreenOpener
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import io.github.fopwoc.mods.palimpsest.client.gui.MapScreen
import io.github.fopwoc.mods.palimpsest.client.gui.PalimpsestScreen
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import net.minecraft.client.Minecraft

@SideOnly(Side.CLIENT)
object PalimpsestCommand :
    ClientCommand(name = "palimpsest", usage = "/palimpsest [bench | flush | where | block]") {
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
            else -> usage
        }

    override fun complete(args: List<String>): List<String> =
        if (args.size == 1) listOf("bench", "flush", "where", "block") else emptyList()

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
}
