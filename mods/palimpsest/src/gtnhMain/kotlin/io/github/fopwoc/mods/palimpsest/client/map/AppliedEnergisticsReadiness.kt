package io.github.fopwoc.mods.palimpsest.client.map

import io.github.fopwoc.mods.framework.log.logger
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.world.ChunkColumns
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import java.lang.reflect.Method
import net.minecraft.block.Block
import net.minecraft.tileentity.TileEntity
import net.minecraft.world.IBlockAccess

/**
 * Defers AE2 cable-bus scans until their separately delivered part payload has arrived. Before that
 * payload, a cable bus is temporarily empty and its fallback icon can cover the block beneath it
 * across the map.
 */
@SideOnly(Side.CLIENT)
object AppliedEnergisticsReadiness : BlockReadiness.Provider, BlockColors.Provider {
    private val logger = logger<AppliedEnergisticsReadiness>()

    private class Api(loader: ClassLoader) {
        val cableBusBlock: Class<*> = loader.loadClass("appeng.block.networking.BlockCableBus")
        val cableBusTile: Class<*> = loader.loadClass("appeng.tile.networking.TileCableBus")
        val isEmpty: Method = cableBusTile.getMethod("isEmpty")
    }

    private val api: Api? =
        runCatching { Api(AppliedEnergisticsReadiness::class.java.classLoader) }
            .onFailure {
                logger.info(
                    "Applied Energistics not found; no cable readiness guard ({})",
                    it.toString(),
                )
            }
            .getOrNull()

    fun register() {
        if (api == null) return
        BlockReadiness.register(this)
        BlockColors.registerProvider(this)
    }

    override fun colorOf(
        world: IBlockAccess,
        x: Int,
        y: Int,
        z: Int,
        block: Block,
        meta: Int,
    ): BlockColors.BlockColor? {
        val api = api ?: return null
        if (!api.cableBusBlock.isInstance(block)) return null
        val static = BlockColors.of(block, meta)
        return BlockColors.blockColor(
            argb = static.argb.takeUnless { it == ChunkColumns.TRANSPARENT } ?: FALLBACK_COLOR,
            tint = static.tint,
            decoration = true,
            variant = "cable-bus",
            detail = "stable cable-bus block",
        )
    }

    @Suppress("TooGenericExceptionCaught")
    override fun isReady(
        world: IBlockAccess,
        x: Int,
        y: Int,
        z: Int,
        block: Block,
        tile: TileEntity?,
    ): Boolean {
        val api = api ?: return true
        if (!api.cableBusBlock.isInstance(block)) return true
        if (tile == null || tile.isInvalid || !api.cableBusTile.isInstance(tile)) return false
        return try {
            !(api.isEmpty.invoke(tile) as Boolean)
        } catch (failure: Exception) {
            logger.debug(
                "AE2 cable readiness at {},{},{} unreadable: {}",
                x,
                y,
                z,
                failure.toString(),
            )
            false
        }
    }

    private const val FALLBACK_COLOR = 0xFF808080.toInt()
}
