/*? if >=26 {*/
// Not ported to 1.21.1 yet: the whole file exists only from 26.x.
package io.github.fopwoc.mods.framework.world.minecraft

import com.mojang.blaze3d.platform.NativeImage
import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.framework.world.ChunkColumns
import io.github.fopwoc.mods.framework.world.TexelAverage
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.block.BlockStateModelSet
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.BlockTags
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.BaseTorchBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.DiodeBlock
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.RedStoneWireBlock
import net.minecraft.world.level.block.TripWireBlock
import net.minecraft.world.level.block.TripWireHookBlock
import net.minecraft.world.level.block.state.BlockState

/**
 * One colour per block state for maps, averaged from the top faces of its baked model as the atlas
 * holds them (so resource packs are honoured) and recomputed after every resource reload. Liquids
 * take their still texture. Blocks that are not full cubes are *decorations*: they show, but the
 * map keeps the height of the ground they stand on. Circuitry (torches, levers, rails, redstone) is
 * see-through, as on GTNH. Client thread only.
 */
object BlockColors {
    /** Which biome colour the map multiplies the block by, if any. */
    enum class Tint {
        NONE,
        GRASS,
        FOLIAGE,
    }

    class BlockColor(
        val argb: Int,
        val tint: Tint,
        val decoration: Boolean,
        /** How the colour came about, for the debug command. */
        val detail: String? = null,
    ) {
        val isTransparent: Boolean
            get() = argb == ChunkColumns.TRANSPARENT
    }

    private val logger = logger<BlockColors>()
    private const val WHITE = 0xFFFFFF
    private const val GREY = 0xFF808080.toInt()
    private val transparent = BlockColor(ChunkColumns.TRANSPARENT, Tint.NONE, false)
    private val byState = HashMap<BlockState, BlockColor>()
    private val bySprite = HashMap<TextureAtlasSprite, TexelAverage.Layer>()
    /** The model set the caches were built against; a reload swaps it. */
    private var models: BlockStateModelSet? = null
    private val random = RandomSource.create()

    /** The colour of [state] at [pos]; the first position a state is seen at decides its tint. */
    fun of(level: ClientLevel, pos: BlockPos, state: BlockState): BlockColor {
        val current = Minecraft.getInstance().modelManager.blockStateModelSet
        if (current !== models) {
            models = current
            byState.clear()
            bySprite.clear()
        }
        return byState.getOrPut(state) { compute(level, pos, state, current) }
    }

    fun isDecoration(level: ClientLevel, pos: BlockPos, state: BlockState): Boolean =
        of(level, pos, state).decoration

    /** Why a block classifies the way it does; for a debug command. */
    fun describe(level: ClientLevel, pos: BlockPos, state: BlockState): String {
        val color = of(level, pos, state)
        return buildString {
            append(BuiltInRegistries.BLOCK.getKey(state.block))
            append(" color=").append("%08X".format(color.argb))
            append(" tint=").append(color.tint.name.lowercase())
            append(" decoration=").append(color.decoration)
            color.detail?.let { append(" layers=").append(it) }
        }
    }

    private fun compute(
        level: ClientLevel,
        pos: BlockPos,
        state: BlockState,
        models: BlockStateModelSet,
    ): BlockColor {
        val block = state.block
        if (state.isAir || isCircuit(block)) return transparent
        val decoration =
            block !is LiquidBlock && !Block.isShapeFullBlock(state.getShape(level, pos))
        val tintSources = Minecraft.getInstance().blockColors
        if (block is LiquidBlock) {
            val fluid =
                Minecraft.getInstance().modelManager.fluidStateModelSet.get(state.fluidState)
            val layer =
                layerOf(fluid.stillMaterial().sprite())
                    ?: return BlockColor(GREY, Tint.NONE, decoration)
            val multiplier = runCatching { fluid.tintSource()?.color(state) }.getOrNull() ?: WHITE
            return blockColor(
                TexelAverage.compose(listOf(tinted(layer, multiplier))),
                Tint.NONE,
                decoration,
                null,
            )
        }
        val quads = topQuads(models, state)
        var tint = Tint.NONE
        val layers = quads.mapNotNull { quad ->
            val info = quad.materialInfo()
            val layer = layerOf(info.sprite()) ?: return@mapNotNull null
            val source =
                if (info.tintIndex() >= 0) tintSources.getTintSource(state, info.tintIndex())
                else null
            if (source == null) return@mapNotNull layer
            // A colour that changes with the position is the biome's (grass, oak leaves) and is
            // applied when the map is drawn; one that does not (spruce leaves) is baked in.
            val static = runCatching { source.color(state) and WHITE }.getOrDefault(WHITE)
            val positional = runCatching {
                source.colorInWorld(state, level, pos) and WHITE
            }
                .getOrDefault(static)
            if (positional != static) {
                if (tint == Tint.NONE)
                    tint = if (state.`is`(BlockTags.LEAVES)) Tint.FOLIAGE else Tint.GRASS
                layer
            } else {
                tinted(layer, static)
            }
        }
        val detail =
            quads.joinToString(",") { it.materialInfo().sprite().contents().name().toString() }
        val color =
            when {
                layers.isNotEmpty() -> TexelAverage.compose(layers)
                // No readable texture: a solid block the map cannot see is worse than a grey one.
                else -> GREY
            }
        return blockColor(color, tint, decoration, detail)
    }

    private fun blockColor(
        argb: Int?,
        tint: Tint,
        decoration: Boolean,
        detail: String?,
    ): BlockColor =
        if (argb == null || argb == ChunkColumns.TRANSPARENT) transparent
        else BlockColor(argb, tint, decoration, detail)

    private fun tinted(layer: TexelAverage.Layer, rgb: Int) =
        TexelAverage.Layer(TexelAverage.multiply(layer.argb, rgb), layer.coverage)

    /** The quads facing up, bottom layer first; any quads for models without a top (plants). */
    private fun topQuads(models: BlockStateModelSet, state: BlockState): List<BakedQuad> {
        val parts = ArrayList<BlockStateModelPart>()
        random.setSeed(state.hashCode().toLong())
        @Suppress("DEPRECATION") models.get(state).collectParts(random, parts)
        val all = parts.flatMap { part -> (Direction.entries + null).flatMap(part::getQuads) }
        return all.filter { it.direction() == Direction.UP }.ifEmpty { all }
    }

    /**
     * The sprite's first frame as a layer; null when its texture cannot be read. Decoded from the
     * resource manager because only NeoForge exposes the atlas's copy of the pixels.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun layerOf(sprite: TextureAtlasSprite): TexelAverage.Layer? {
        bySprite[sprite]?.let {
            return it
        }
        val contents = sprite.contents()
        val texture = contents.name().withPath { "textures/$it.png" }
        return try {
            NativeImage.read(Minecraft.getInstance().resourceManager.open(texture))
                .use { image ->
                    // An animation strip stacks frames; the first one is the top-left frame.
                    TexelAverage.layer(
                        minOf(contents.width(), image.width),
                        minOf(contents.height(), image.height),
                        image::getPixel,
                    )
                }
                ?.also { bySprite[sprite] = it }
        } catch (failure: Exception) {
            logger.debug("No readable texture for {}: {}", texture, failure.toString())
            null
        }
    }

    private fun isCircuit(block: Block): Boolean =
        block is BaseTorchBlock ||
            block is LeverBlock ||
            block is ButtonBlock ||
            block is RedStoneWireBlock ||
            block is DiodeBlock ||
            block is BaseRailBlock ||
            block is TripWireBlock ||
            block is TripWireHookBlock ||
            block is LadderBlock
}
/*?}*/
