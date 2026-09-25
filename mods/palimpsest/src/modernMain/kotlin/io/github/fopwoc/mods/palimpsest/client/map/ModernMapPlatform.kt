package io.github.fopwoc.mods.palimpsest.client.map

import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import io.github.fopwoc.mods.framework.world.minecraft.BiomeTints as GameBiomeTints

/**
 * Minecraft 26.x: dimensions by key (`minecraft_overworld/`), surface scanned from the top of the
 * dimension's build height. Heights outside 0..255 flatten; see `TileRecord.build`.
 */
object ModernMapPlatform : MapPlatform {
    override fun location(): MapLocation? {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return null
        if (minecraft.player == null) return null
        val worldId = ClientBackend.current.currentWorldId ?: return null
        val dimension = level.dimension().identifier().toString().replace(Regex("[^A-Za-z0-9._-]"), "_")
        return MapLocation(worldId, dimension, level.maxY)
    }

    override fun biomeTints(): BiomeTints {
        val level = checkNotNull(Minecraft.getInstance().level) { "Map tints need a level" }
        val grass = GameBiomeTints.table(level)
        val foliage = GameBiomeTints.foliageTable(level)
        val water = GameBiomeTints.waterTable(level)
        return BiomeTints(grass = grass::get, foliage = foliage::get, water = water::get)
    }

    override fun scanner(session: MapSession): MapScanner = ModernChunkScanner(session)

    /** The first blocks at or below the player's feet that the map would consider, and why. */
    override fun describeBlocksBelow(): String {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return "No player"
        val level = minecraft.level ?: return "No world"
        val pos = BlockPos.MutableBlockPos(player.blockX, player.blockY - 1, player.blockZ)
        val lines = ArrayList<String>()
        while (pos.y >= level.minY && lines.size < 4) {
            val state = level.getBlockState(pos)
            if (!state.isAir) lines += "y=${pos.y} ${BlockColors.describe(level, pos, state)}"
            pos.move(0, -1, 0)
        }
        return lines.joinToString("\n").ifEmpty { "Nothing below" }
    }
}
