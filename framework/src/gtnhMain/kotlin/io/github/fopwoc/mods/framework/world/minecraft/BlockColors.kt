package io.github.fopwoc.mods.framework.world.minecraft

import io.github.fopwoc.mods.framework.log.logger
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.world.ChunkColumns
import io.github.fopwoc.mods.framework.world.TexelAverage
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import net.minecraft.block.Block
import net.minecraft.block.BlockAnvil
import net.minecraft.block.material.MapColor
import net.minecraft.block.material.Material
import net.minecraft.client.Minecraft
import net.minecraft.util.IIcon
import net.minecraft.util.ResourceLocation
import net.minecraft.world.IBlockAccess
import net.minecraftforge.client.event.TextureStitchEvent
import net.minecraftforge.common.MinecraftForge

/**
 * One color per block, metadata and texture, averaged from the block's top texture as it shows at a
 * position in the world, computed on first use and forgotten whenever Forge re-stitches the block
 * atlas (so resource packs are honoured).
 *
 * The texture is decoded from the resource manager rather than read off the stitched sprite,
 * because the atlas drops sprite pixel data right after upload — before the stitch event fires. The
 * icon is asked position-aware (`getIcon(world, x, y, z, side)`), which is how machines whose look
 * lives in a tile entity — every GregTech machine — report their real texture. Every block but air
 * and circuitry (torches, levers, redstone) is a surface: plants, slabs, frames and glass draw with
 * the colour of their opaque texels, and only a texture with almost no opaque texels (string) is
 * see-through. A block without a readable texture takes its map colour, and a block with neither is
 * grey rather than invisible. Blocks that are not full cubes are *decorations*: they show, but the
 * map keeps the height of the ground they stand on, so a meadow does not shade like a rockslide.
 */
@SideOnly(Side.CLIENT)
object BlockColors {
    /**
     * A block's untinted average color and whether the biome's grass color is applied over it at
     * render time. Tintable is a property of the block (its texture is greyscale by design and the
     * game colors it per biome), never inferred from the color.
     */
    /** Which biome colour the game multiplies the block by, if any. */
    enum class Tint {
        NONE,
        GRASS,
        FOLIAGE,
    }

    class BlockColor(
        val argb: Int,
        val tint: Tint,
        val decoration: Boolean,
        /**
         * What identifies this look for the map's vocabulary, beyond the block's name: the icon
         * shown and any baked colour — never metadata, which mods use for transient state (formed,
         * lit, decaying) that would make an unchanged block look changed.
         */
        val variant: String?,
        /** How the colour came about, for the debug command. */
        val detail: String? = null,
        /** Stable structural variant for history; null uses the block's dropped metadata. */
        val identity: String? = null,
    ) {
        val isTransparent: Boolean
            get() = argb == ChunkColumns.TRANSPARENT

        val tintable: Boolean
            get() = tint != Tint.NONE
    }

    /**
     * One texture as a layer: its average colour over opaque texels and how much of it is opaque,
     * so layers can be composited the way the renderer stacks them.
     */
    class IconLayer(val argb: Int, val coverage: Int)

    /**
     * A mod-specific way to colour a block at a position, consulted before the generic texture
     * path; returns null to decline. [BlockColor.variant] identifies the rendered look, while
     * [BlockColor.identity] identifies stable structure for map history.
     */
    fun interface Provider {
        fun colorOf(
            world: IBlockAccess,
            x: Int,
            y: Int,
            z: Int,
            block: Block,
            meta: Int,
        ): BlockColor?
    }

    private val providers = java.util.concurrent.CopyOnWriteArrayList<Provider>()

    fun registerProvider(provider: Provider) {
        providers += provider
    }

    private const val TOP = 1
    private const val ANVIL_TOP_RENDER_SIDE = 3
    private const val OPAQUE_ALPHA = TexelAverage.OPAQUE_ALPHA
    private const val WHITE = 0xFFFFFF
    private const val GREY = 0xFF808080.toInt()
    private val logger = logger<BlockColors>()
    private var registered = false
    private val byBlock = ConcurrentHashMap<String, BlockColor>()
    private val byIcon = ConcurrentHashMap<String, Int>()
    private val layers = ConcurrentHashMap<String, Long>()
    private const val MISSING_LAYER = -1L
    private val transparent = BlockColor(ChunkColumns.TRANSPARENT, Tint.NONE, false, null)

    /**
     * Increments on every atlas stitch so palettes and caches can notice a resource pack change.
     */
    @Volatile
    var version = 0
        private set

    fun register() {
        if (registered) return
        registered = true
        MinecraftForge.EVENT_BUS.register(this)
    }

    /**
     * The colour of the block at a world position, keyed by block, metadata and the icon it shows
     * there.
     */
    fun of(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block, meta: Int): BlockColor {
        for (provider in providers) provider.colorOf(world, x, y, z, block, meta)?.let {
            return it
        }
        val icon = iconAt(world, x, y, z, block) ?: staticIcon(block, meta)
        val decoration = !block.material.isLiquid && !isFullCube(world, x, y, z, block)
        // A colour that changes with the position is the biome's (oak leaves, grass) and is applied
        // live; one that does not (spruce leaves, GregTech frames, dyed blocks) is part of the look
        // and is baked in.
        val positional =
            runCatching { block.colorMultiplier(world, x, y, z) and WHITE }.getOrDefault(WHITE)
        val static = runCatching { block.getRenderColor(meta) and WHITE }.getOrDefault(WHITE)
        val tint = if (positional != static) tintOf(block) else Tint.NONE
        val multiplier = if (tint == Tint.NONE) positional else WHITE
        // The full metadata: EndlessIDs gives blocks 16 bits of it, and GregTech ores use them.
        return byBlock.getOrPut(
            "${Block.getIdFromBlock(block)}:$meta:${icon?.iconName}:$multiplier:$tint:$decoration"
        ) {
            val variant =
                listOfNotNull(
                        look(icon?.iconName ?: "none", decoration),
                        "m%06X".format(multiplier).takeIf { multiplier != WHITE },
                    )
                    .joinToString("/")
            compute(block, meta, icon, variant, multiplier, tint, decoration)
        }
    }

    /**
     * The identity an icon gives a block. A decoration's growth stage is state, not identity: a
     * field of wheat is the same field at every stage, so `wheat_stage_7` counts as `wheat_stage`
     * and the look is frozen at whichever stage was seen first.
     */
    private fun look(iconName: String, decoration: Boolean): String =
        if (decoration) STAGE_SUFFIX.replace(iconName, "") else iconName

    private val STAGE_SUFFIX = Regex("_(stage_)?\\d+$")

    /**
     * Which biome colour a position-dependent block follows: leaves the foliage colour, the rest
     * grass.
     */
    private fun tintOf(block: Block): Tint =
        if (block.material === Material.leaves) Tint.FOLIAGE else Tint.GRASS

    /** The colour of a block as its static icon shows it, for tools without a world position. */
    fun of(block: Block, meta: Int): BlockColor {
        val icon = staticIcon(block, meta)
        return byBlock.getOrPut(
            "${Block.getIdFromBlock(block)}:$meta:${icon?.iconName}:$WHITE:${Tint.NONE}"
        ) {
            compute(
                block,
                meta,
                icon,
                null,
                WHITE,
                Tint.NONE,
                decoration = !block.material.isLiquid && !isFullCube(block),
            )
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    internal fun iconAt(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block): IIcon? =
        try {
            withStableRenderState(block) { block.getIcon(world, x, y, z, TOP) }
        } catch (failure: Exception) {
            null
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun staticIcon(block: Block, meta: Int): IIcon? =
        try {
            // Blocks index icon arrays by metadata and throw on values they never use.
            withStableRenderState(block) { block.getIcon(TOP, meta) }
        } catch (failure: Exception) {
            null
        }

    /**
     * Vanilla's anvil renderer selects its top face through a mutable field on the singleton block
     * rather than through [Block.getIcon]. Without establishing that state, an anvil changes from
     * its base icon to its top icon after the first render and looks like a map edit.
     */
    private inline fun <T> withStableRenderState(block: Block, lookup: () -> T): T {
        if (block !is BlockAnvil) return lookup()
        val previous = block.anvilRenderSide
        return try {
            block.anvilRenderSide = ANVIL_TOP_RENDER_SIDE
            lookup()
        } finally {
            block.anvilRenderSide = previous
        }
    }

    @SubscribeEvent
    fun onStitch(event: TextureStitchEvent.Post) {
        if (event.map.textureType != 0) return
        byBlock.clear()
        byIcon.clear()
        layers.clear()
        version++
    }

    /** Why a block classifies the way it does; for a debug command. */
    fun describe(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block, meta: Int): String {
        val icon = iconAt(world, x, y, z, block) ?: staticIcon(block, meta)
        return buildString {
            append(Block.blockRegistry.getNameForObject(block)).append(':').append(meta)
            append(" fullCube=").append(isFullCube(world, x, y, z, block))
            append(" liquid=").append(block.material.isLiquid)
            append(" icon=").append(icon?.iconName ?: "none")
            append(" texture=")
                .append(icon?.let { textureAverage(it) }?.let { "%08X".format(it) } ?: "none")
            val color = of(world, x, y, z, block, meta)
            append(" color=").append("%08X".format(color.argb))
            append(" tint=").append(color.tint.name.lowercase())
            append(" decoration=").append(color.decoration)
            color.variant?.let { append(" variant=").append(it) }
            color.identity?.let { append(" identity=").append(it) }
            color.detail?.let { append(" layers=").append(it) }
        }
    }

    /**
     * Untinted colour with the baked multiplier, plus which live biome tint (if any) applies at
     * render.
     */
    private fun compute(
        block: Block,
        meta: Int,
        icon: IIcon?,
        variant: String?,
        multiplier: Int,
        tint: Tint,
        decoration: Boolean,
    ): BlockColor {
        // Circuits: torches, levers, buttons, redstone dust, tripwire — clutter, not surface.
        if (block.material === Material.air || block.material === Material.circuits)
            return transparent
        val textured = icon?.let(::textureAverage)
        val color =
            (textured ?: fallback(block, meta)).let {
                if (it == ChunkColumns.TRANSPARENT) it else multiply(it, multiplier)
            }
        if (color == ChunkColumns.TRANSPARENT) return transparent
        return BlockColor(
            color,
            tint,
            decoration = decoration,
            variant = variant,
        )
    }

    private fun multiply(argb: Int, rgb: Int): Int = TexelAverage.multiply(argb, rgb)

    /** Static bounds fill the whole block and it renders as a plain cube; graphics-setting free. */
    fun isFullCube(block: Block): Boolean =
        block.renderAsNormalBlock() &&
            block.blockBoundsMinX == 0.0 &&
            block.blockBoundsMinY == 0.0 &&
            block.blockBoundsMinZ == 0.0 &&
            block.blockBoundsMaxX == 1.0 &&
            block.blockBoundsMaxY == 1.0 &&
            block.blockBoundsMaxZ == 1.0

    /**
     * Whether the block fills its cube at this position. Minecraft stores bounds on the singleton
     * block object, so dynamic blocks must establish their position-specific bounds before they are
     * inspected. The previous bounds are restored because rendering uses the same singleton.
     */
    fun isFullCube(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block): Boolean {
        val bounds =
            doubleArrayOf(
                block.blockBoundsMinX,
                block.blockBoundsMinY,
                block.blockBoundsMinZ,
                block.blockBoundsMaxX,
                block.blockBoundsMaxY,
                block.blockBoundsMaxZ,
            )
        val fallback = isFullCube(block)
        return try {
            block.setBlockBoundsBasedOnState(world, x, y, z)
            isFullCube(block)
        } catch (_: Exception) {
            fallback
        } finally {
            block.setBlockBounds(
                bounds[0].toFloat(),
                bounds[1].toFloat(),
                bounds[2].toFloat(),
                bounds[3].toFloat(),
                bounds[4].toFloat(),
                bounds[5].toFloat(),
            )
        }
    }

    /** The texture of an icon as a layer; null when it cannot be read. */
    fun layerOf(icon: IIcon): IconLayer? {
        val name = icon.iconName ?: return null
        val packed = layers.getOrPut(name) { decodeLayer(name) ?: MISSING_LAYER }
        if (packed == MISSING_LAYER) return null
        return IconLayer((packed ushr 8).toInt(), (packed and 0xFF).toInt())
    }

    /**
     * The static texture of one [side] of a block as a layer, e.g. for a texture that copies
     * another block.
     */
    fun layerOf(block: Block, meta: Int, side: Int = TOP): IconLayer? =
        runCatching { block.getIcon(side, meta) }.getOrNull()?.let(::layerOf)

    /** Composites layers bottom-up by coverage; null when nothing is visible. */
    fun compose(layers: List<IconLayer>): Int? = TexelAverage.compose(layers.map { TexelAverage.Layer(it.argb, it.coverage) })

    /** A provider's colour, cached under its own key until the atlas is stitched again. */
    fun cached(key: String, compute: () -> BlockColor): BlockColor = byBlock.getOrPut(key, compute)

    fun blockColor(
        argb: Int,
        tint: Tint,
        decoration: Boolean,
        variant: String?,
        detail: String? = null,
        identity: String? = null,
    ): BlockColor =
        if (argb == ChunkColumns.TRANSPARENT) transparent
        else BlockColor(argb, tint, decoration, variant, detail, identity)

    /** Average of the opaque texels of the texture's first frame; null when unreadable. */
    private fun textureAverage(icon: IIcon): Int? {
        val name = icon.iconName ?: return null
        return byIcon.getOrPut(name) { decodeAverage(name) ?: MISSING }.takeIf { it != MISSING }
    }

    private fun decodeAverage(iconName: String): Int? {
        val packed = decodeLayer(iconName) ?: return null
        val coverage = (packed and 0xFF).toInt()
        return if (coverage < OPAQUE_ALPHA) ChunkColumns.TRANSPARENT else (packed ushr 8).toInt()
    }

    /** Colour over the opaque texels and the average alpha, packed `argb << 8 | coverage`. */
    @Suppress("TooGenericExceptionCaught")
    private fun decodeLayer(iconName: String): Long? {
        val colon = iconName.indexOf(':')
        val domain = if (colon > 0) iconName.substring(0, colon) else "minecraft"
        val path = if (colon > 0) iconName.substring(colon + 1) else iconName
        val location = ResourceLocation(domain, "textures/blocks/$path.png")
        return try {
            val image =
                Minecraft.getMinecraft()
                    .resourceManager
                    .getResource(location)
                    .inputStream
                    .use(ImageIO::read) ?: return null
            // An animation strip is a vertical stack of frames; the first frame is the top square.
            val side = image.width
            val layer = TexelAverage.layer(side, minOf(side, image.height), image::getRGB) ?: return 0L
            (layer.argb.toLong() and 0xFFFFFFFFL shl 8) or layer.coverage.toLong()
        } catch (failure: Exception) {
            logger.debug("No readable texture for {}: {}", location, failure.toString())
            null
        }
    }

    /** Map colour, or grey: a solid block the map cannot see is worse than a grey one. */
    private fun fallback(block: Block, meta: Int): Int {
        val mapColor = runCatching { block.getMapColor(meta) }.getOrNull() ?: return GREY
        return if (mapColor === MapColor.airColor) GREY else (0xFF shl 24) or mapColor.colorValue
    }

    /** Sentinel in [byIcon] for textures that could not be decoded, so they are not retried. */
    private const val MISSING = 1
}
