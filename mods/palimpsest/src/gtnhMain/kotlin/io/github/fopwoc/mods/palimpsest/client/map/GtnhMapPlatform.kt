package io.github.fopwoc.mods.palimpsest.client.map

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientWorldContext
import io.github.fopwoc.mods.framework.world.minecraft.BiomeTints as GameBiomeTints
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import net.minecraft.client.Minecraft

/** Minecraft 1.7.10: numeric dimensions (`dim<N>/`), surface scanned from Y=255. */
@SideOnly(Side.CLIENT)
object GtnhMapPlatform : MapPlatform {
    private const val SURFACE_CEILING = 255
    private const val WHITE = 0xFFFFFF

    fun install() {
        BlockColors.register()
        GregTechColors.register()
        AppliedEnergisticsReadiness.register()
    }

    override fun location(): MapLocation? {
        val minecraft = Minecraft.getMinecraft()
        val world = minecraft.theWorld ?: return null
        if (minecraft.thePlayer == null) return null
        val worldId = ClientWorldContext.currentId(minecraft) ?: return null
        return MapLocation(worldId, "dim${world.provider.dimensionId}", SURFACE_CEILING)
    }

    override fun biomeTints(): BiomeTints {
        val grass = GameBiomeTints.table()
        val foliage = GameBiomeTints.foliageTable()
        val water = GameBiomeTints.waterTable()
        return BiomeTints(
            grass = { grass.getOrElse(it) { WHITE } },
            foliage = { foliage.getOrElse(it) { WHITE } },
            water = { water.getOrElse(it) { WHITE } },
        )
    }

    override fun scanner(session: MapSession): MapScanner = ChunkScanner(session)

    /** The first blocks at or below the player's feet that the map would consider, and why. */
    override fun describeBlocksBelow(): String {
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
                lines +=
                    "y=$y ${BlockColors.describe(world, x, y, z, block, world.getBlockMetadata(x, y, z))}"
            }
            y--
        }
        return lines.joinToString("\n").ifEmpty { "Nothing below" }
    }
}
