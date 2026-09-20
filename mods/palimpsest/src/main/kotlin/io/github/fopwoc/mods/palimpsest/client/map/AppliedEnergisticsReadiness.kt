package io.github.fopwoc.mods.palimpsest.client.map

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import java.lang.reflect.Method
import net.minecraft.block.Block
import net.minecraft.tileentity.TileEntity
import net.minecraft.world.IBlockAccess
import org.apache.logging.log4j.LogManager

/**
 * Defers AE2 cable-bus scans until their separately delivered part payload has arrived. Before that
 * payload, a cable bus is temporarily empty and its fallback icon can cover the block beneath it
 * across the map.
 */
@SideOnly(Side.CLIENT)
object AppliedEnergisticsReadiness : BlockReadiness.Provider {
    private val logger = LogManager.getLogger(AppliedEnergisticsReadiness::class.java)

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
        if (api != null) BlockReadiness.register(this)
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
}
