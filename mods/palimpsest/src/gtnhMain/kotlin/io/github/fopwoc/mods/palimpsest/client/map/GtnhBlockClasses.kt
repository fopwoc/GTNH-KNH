package io.github.fopwoc.mods.palimpsest.client.map

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import io.github.fopwoc.mods.palimpsest.history.BlockClass
import io.github.fopwoc.palimpsest.db.BlockKind
import net.minecraft.block.Block
import net.minecraft.block.material.Material

/**
 * How the map treats 1.7.10 blocks: [BlockKind] from the material and the block's look, colour from
 * the look. The same rules the live scan applies through `ChunkColumnsAdapter`. Game thread.
 */
@SideOnly(Side.CLIENT)
object GtnhBlockClasses {
    fun of(block: Block, look: BlockColors.BlockColor): BlockClass =
        BlockClass(kindOf(block, look), look.argb and WHITE, look.tint.ordinal)

    /** From a history identity, `name:meta` or `name@identity`, by the block's static look. */
    fun of(identity: String): BlockClass? {
        val name =
            identity.substringBefore('@').let {
                if ('@' in identity) it else it.substringBeforeLast(':')
            }
        val meta = if ('@' in identity) 0 else identity.substringAfterLast(':').toIntOrNull() ?: 0
        if (!Block.blockRegistry.containsKey(name)) return null
        val block = Block.blockRegistry.getObject(name) as? Block ?: return null
        return of(block, BlockColors.of(block, meta))
    }

    private fun kindOf(block: Block, look: BlockColors.BlockColor): BlockKind =
        when {
            block.material === Material.air -> BlockKind.AIR
            block.material === Material.water -> BlockKind.WATER
            block.material.isLiquid -> BlockKind.LIQUID
            look.isTransparent -> BlockKind.TRANSPARENT
            look.decoration -> BlockKind.DECORATION
            else -> BlockKind.SOLID
        }

    private const val WHITE = 0xFFFFFF
}
