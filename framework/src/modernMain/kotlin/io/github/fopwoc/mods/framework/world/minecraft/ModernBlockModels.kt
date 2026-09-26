/*? if >=26 {*/
// 26.x only: model parts and tint sources; LegacyBlockModels reads 1.21.1's baked models.
package io.github.fopwoc.mods.framework.world.minecraft

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState

/** What [BlockColors] reads from Minecraft 26.x's models. Client thread only. */
internal object ModernBlockModels {
    private const val WHITE = 0xFFFFFF

    /** Replaced by every resource reload. */
    fun generation(): Any = Minecraft.getInstance().modelManager.blockStateModelSet

    /** The quads facing up, bottom layer first; any quads for models without a top (plants). */
    fun topQuads(state: BlockState, random: RandomSource): List<QuadMaterial> {
        val parts = ArrayList<BlockStateModelPart>()
        random.setSeed(state.hashCode().toLong())
        @Suppress("DEPRECATION")
        Minecraft.getInstance()
            .modelManager
            .blockStateModelSet
            .get(state)
            .collectParts(random, parts)
        val all = parts.flatMap { part -> (Direction.entries + null).flatMap(part::getQuads) }
        return all.filter { it.direction() == Direction.UP }
            .ifEmpty { all }
            .map { QuadMaterial(it.materialInfo().sprite(), it.materialInfo().tintIndex()) }
    }

    fun fluid(state: BlockState): FluidMaterial {
        val fluid = Minecraft.getInstance().modelManager.fluidStateModelSet.get(state.fluidState)
        val multiplier = runCatching { fluid.tintSource()?.color(state) }.getOrNull() ?: WHITE
        return FluidMaterial(fluid.stillMaterial().sprite(), multiplier)
    }

    fun tinting(level: ClientLevel, pos: BlockPos, state: BlockState, tintIndex: Int): Tinting? {
        val source =
            Minecraft.getInstance().blockColors.getTintSource(state, tintIndex) ?: return null
        val static = runCatching { source.color(state) and WHITE }.getOrDefault(WHITE)
        val positional = runCatching {
            source.colorInWorld(state, level, pos) and WHITE
        }
            .getOrDefault(static)
        return Tinting(static, positional)
    }

    fun argb(image: NativeImage, x: Int, y: Int): Int = image.getPixel(x, y)
}
/*?}*/
