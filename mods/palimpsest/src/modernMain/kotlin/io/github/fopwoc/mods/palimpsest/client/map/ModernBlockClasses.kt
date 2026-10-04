package io.github.fopwoc.mods.palimpsest.client.map

import io.github.fopwoc.mods.framework.minecraft.Identifier
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import io.github.fopwoc.mods.palimpsest.history.BlockClass
import io.github.fopwoc.palimpsest.db.BlockKind
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState

/**
 * How the map treats modern blocks: [BlockKind] from the state and its look, colour from the look.
 * The same rules the live scan applies through `ChunkColumnsAdapter`. Game thread.
 */
object ModernBlockClasses {
    fun of(state: BlockState, look: BlockColors.BlockColor): BlockClass =
        BlockClass(kindOf(state, look), look.argb and WHITE, look.tint.ordinal)

    /** From a history identity, the block's registry key, by its default state's look. */
    fun of(identity: String): BlockClass? {
        val key = Identifier.tryParse(identity) ?: return null
        if (!BuiltInRegistries.BLOCK.containsKey(key)) return null
        val level = Minecraft.getInstance().level ?: return null
        /*? if >=26 {*/
        val block = BuiltInRegistries.BLOCK.getValue(key)
        /*?} else {*/
        /*val block = BuiltInRegistries.BLOCK.get(key)
         */
        /*?}*/
        val state = block.defaultBlockState()
        return of(state, BlockColors.of(level, BlockPos.ZERO, state))
    }

    private fun kindOf(state: BlockState, look: BlockColors.BlockColor): BlockKind =
        when {
            state.isAir -> BlockKind.AIR
            state.block is LiquidBlock && state.fluidState.`is`(FluidTags.WATER) -> BlockKind.WATER
            state.block is LiquidBlock -> BlockKind.LIQUID
            look.isTransparent -> BlockKind.TRANSPARENT
            look.decoration -> BlockKind.DECORATION
            else -> BlockKind.SOLID
        }

    private const val WHITE = 0xFFFFFF
}
