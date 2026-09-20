package io.github.fopwoc.mods.palimpsest.client.map

import java.util.concurrent.CopyOnWriteArrayList
import net.minecraft.block.Block
import net.minecraft.tileentity.TileEntity
import net.minecraft.world.IBlockAccess

/**
 * Opt-in guards for blocks whose stable map appearance depends on asynchronously delivered client
 * data. Ordinary blocks are ready immediately; integrations reject only states they understand.
 */
object BlockReadiness {
    fun interface Provider {
        fun isReady(
            world: IBlockAccess,
            x: Int,
            y: Int,
            z: Int,
            block: Block,
            tile: TileEntity?,
        ): Boolean
    }

    private val providers = CopyOnWriteArrayList<Provider>()

    fun register(provider: Provider) {
        providers += provider
    }

    fun isReady(
        world: IBlockAccess,
        x: Int,
        y: Int,
        z: Int,
        block: Block,
        tile: TileEntity?,
    ): Boolean = providers.all { it.isReady(world, x, y, z, block, tile) }
}
