/*? if <26 {*/
/*package io.github.fopwoc.mods.framework.world.minecraft

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.FluidTags
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState

// What BlockColors reads from Minecraft 1.21.1's baked models, like ModernBlockModels on 26.x.
// Client thread only.
internal object LegacyBlockModels {
    private const val WHITE = 0xFFFFFF
    // What BlockColors.getColor answers for a block without a tint.
    private const val NO_TINT = -1

    // A reload bakes a new missing model along with everything else.
    fun generation(): Any = Minecraft.getInstance().modelManager.missingModel

    // The quads facing up; any quads for models without a top (plants).
    fun topQuads(state: BlockState, random: RandomSource): List<QuadMaterial> {
        val model = Minecraft.getInstance().blockRenderer.blockModelShaper.getBlockModel(state)
        val all =
            (Direction.entries + null).flatMap { side ->
                random.setSeed(state.hashCode().toLong())
                model.getQuads(state, side, random)
            }
        return all
            .filter { it.direction == Direction.UP }
            .ifEmpty { all }
            .map { QuadMaterial(it.sprite, it.tintIndex) }
    }

    // Fluid sprites are private to the liquid renderer; a fluid block's particle is its still
    // texture. Water takes vanilla's default colour, as 26.x bakes in; biomes tint it relative
    // to that when the map is drawn.
    fun fluid(state: BlockState): FluidMaterial {
        val sprite = Minecraft.getInstance().blockRenderer.blockModelShaper.getParticleIcon(state)
        val water = state.fluidState.`is`(FluidTags.WATER)
        return FluidMaterial(sprite, if (water) BiomeTints.DEFAULT_WATER else WHITE)
    }

    fun tinting(level: ClientLevel, pos: BlockPos, state: BlockState, tintIndex: Int): Tinting? {
        val colors = Minecraft.getInstance().blockColors
        val static = runCatching { colors.getColor(state, null, null, tintIndex) }.getOrDefault(NO_TINT)
        val positional =
            runCatching { colors.getColor(state, level, pos, tintIndex) }.getOrDefault(static)
        if (static == NO_TINT && positional == NO_TINT) return null
        fun rgb(color: Int) = if (color == NO_TINT) WHITE else color and WHITE
        return Tinting(rgb(static), rgb(positional))
    }

    // NativeImage holds ABGR on 1.21.1.
    fun argb(image: NativeImage, x: Int, y: Int): Int {
        val abgr = image.getPixelRGBA(x, y)
        return (abgr and 0xFF00FF00.toInt()) or (abgr shr 16 and 0xFF) or (abgr and 0xFF shl 16)
    }
}
*//*?}*/
