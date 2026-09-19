package io.github.fopwoc.mods.framework.world.minecraft

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.world.ChunkColumns
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import net.minecraft.block.Block
import net.minecraft.block.material.MapColor
import net.minecraft.block.material.Material
import net.minecraft.client.Minecraft
import net.minecraft.util.IIcon
import net.minecraft.util.ResourceLocation
import net.minecraft.world.IBlockAccess
import net.minecraftforge.client.event.TextureStitchEvent
import net.minecraftforge.common.MinecraftForge
import org.apache.logging.log4j.LogManager

/**
 * One color per block, metadata and texture, averaged from the block's top texture as it shows at
 * a position in the world, computed on first use and forgotten whenever Forge re-stitches the
 * block atlas (so resource packs are honoured).
 *
 * The texture is decoded from the resource manager rather than read off the stitched sprite,
 * because the atlas drops sprite pixel data right after upload — before the stitch event fires.
 * The icon is asked position-aware (`getIcon(world, x, y, z, side)`), which is how machines whose
 * look lives in a tile entity — every GregTech machine — report their real texture. Every block
 * but air and circuitry (torches, levers, redstone) is a surface: plants, slabs, frames and glass
 * draw with the colour of their opaque texels, and only a texture with almost no opaque texels
 * (string) is see-through. A
 * block without a readable texture takes its map colour, and a block with neither is grey rather
 * than invisible. Blocks that are not full cubes are *decorations*: they show, but the map keeps
 * the height of the ground they stand on, so a meadow does not shade like a rockslide.
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
     * path; returns null to decline. [BlockColor.variant] should identify the look, never a
     * transient state such as a machine being active.
     */
    fun interface Provider {
        fun colorOf(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block, meta: Int): BlockColor?
    }

    private val providers = java.util.concurrent.CopyOnWriteArrayList<Provider>()

    fun registerProvider(provider: Provider) {
        providers += provider
    }

    private const val TOP = 1
    /** Average alpha over the texture below which a block is see-through: torches, string. */
    private const val OPAQUE_ALPHA = 10
    private const val WHITE = 0xFFFFFF
    private const val GREY = 0xFF808080.toInt()
    private val logger = LogManager.getLogger(BlockColors::class.java)
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

    /** The colour of the block at a world position, keyed by block, metadata and the icon it shows there. */
    fun of(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block, meta: Int): BlockColor {
        for (provider in providers) provider.colorOf(world, x, y, z, block, meta)?.let { return it }
        val icon = worldIcon(world, x, y, z, block) ?: staticIcon(block, meta)
        // A colour that changes with the position is the biome's (oak leaves, grass) and is applied
        // live; one that does not (spruce leaves, GregTech frames, dyed blocks) is part of the look
        // and is baked in.
        val positional = runCatching { block.colorMultiplier(world, x, y, z) and WHITE }.getOrDefault(WHITE)
        val static = runCatching { block.getRenderColor(meta) and WHITE }.getOrDefault(WHITE)
        val tint = if (positional != static) tintOf(block) else Tint.NONE
        val multiplier = if (tint == Tint.NONE) positional else WHITE
        // The full metadata: EndlessIDs gives blocks 16 bits of it, and GregTech ores use them.
        return byBlock.getOrPut("${Block.getIdFromBlock(block)}:$meta:${icon?.iconName}:$multiplier:$tint") {
            val variant =
                listOfNotNull(
                        icon?.iconName ?: "none",
                        "m%06X".format(multiplier).takeIf { multiplier != WHITE },
                    )
                    .joinToString("/")
            compute(block, meta, icon, variant, multiplier, tint)
        }
    }

    /** Which biome colour a position-dependent block follows: leaves the foliage colour, the rest grass. */
    private fun tintOf(block: Block): Tint = if (block.material === Material.leaves) Tint.FOLIAGE else Tint.GRASS

    /** The colour of a block as its static icon shows it, for tools without a world position. */
    fun of(block: Block, meta: Int): BlockColor {
        val icon = staticIcon(block, meta)
        return byBlock.getOrPut("${Block.getIdFromBlock(block)}:$meta:${icon?.iconName}:$WHITE:${Tint.NONE}") { compute(block, meta, icon, null, WHITE, Tint.NONE) }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun worldIcon(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block): IIcon? =
        try {
            block.getIcon(world, x, y, z, TOP)
        } catch (failure: Exception) {
            null
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun staticIcon(block: Block, meta: Int): IIcon? =
        try {
            // Blocks index icon arrays by metadata and throw on values they never use.
            block.getIcon(TOP, meta)
        } catch (failure: Exception) {
            null
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
        val icon = worldIcon(world, x, y, z, block) ?: staticIcon(block, meta)
        return buildString {
            append(Block.blockRegistry.getNameForObject(block)).append(':').append(meta)
            append(" fullCube=").append(isFullCube(block))
            append(" liquid=").append(block.material.isLiquid)
            append(" icon=").append(icon?.iconName ?: "none")
            append(" texture=")
                .append(icon?.let { textureAverage(it) }?.let { "%08X".format(it) } ?: "none")
            val color = of(world, x, y, z, block, meta)
            append(" color=").append("%08X".format(color.argb))
            append(" tint=").append(color.tint.name.lowercase())
            append(" decoration=").append(color.decoration)
            color.variant?.let { append(" variant=").append(it) }
            color.detail?.let { append(" layers=").append(it) }
        }
    }

    /** Untinted colour with the baked multiplier, plus which live biome tint (if any) applies at render. */
    private fun compute(block: Block, meta: Int, icon: IIcon?, variant: String?, multiplier: Int, tint: Tint): BlockColor {
        // Circuits: torches, levers, buttons, redstone dust, tripwire — clutter, not surface.
        if (block.material === Material.air || block.material === Material.circuits) return transparent
        val textured = icon?.let(::textureAverage)
        val color = (textured ?: fallback(block, meta)).let { if (it == ChunkColumns.TRANSPARENT) it else multiply(it, multiplier) }
        if (color == ChunkColumns.TRANSPARENT) return transparent
        return BlockColor(color, tint, decoration = !block.material.isLiquid && !isFullCube(block), variant = variant)
    }

    private fun multiply(argb: Int, rgb: Int): Int {
        if (rgb == WHITE) return argb
        val r = (argb shr 16 and 255) * (rgb shr 16 and 255) / 255
        val g = (argb shr 8 and 255) * (rgb shr 8 and 255) / 255
        val b = (argb and 255) * (rgb and 255) / 255
        return (argb and (0xFF shl 24)) or (r shl 16) or (g shl 8) or b
    }

    /** Static bounds fill the whole block and it renders as a plain cube; graphics-setting free. */
    fun isFullCube(block: Block): Boolean =
        block.renderAsNormalBlock() &&
            block.blockBoundsMinX == 0.0 &&
            block.blockBoundsMinY == 0.0 &&
            block.blockBoundsMinZ == 0.0 &&
            block.blockBoundsMaxX == 1.0 &&
            block.blockBoundsMaxY == 1.0 &&
            block.blockBoundsMaxZ == 1.0

    /** The texture of an icon as a layer; null when it cannot be read. */
    fun layerOf(icon: IIcon): IconLayer? {
        val name = icon.iconName ?: return null
        val packed = layers.getOrPut(name) { decodeLayer(name) ?: MISSING_LAYER }
        if (packed == MISSING_LAYER) return null
        return IconLayer((packed ushr 8).toInt(), (packed and 0xFF).toInt())
    }

    /** The static texture of one [side] of a block as a layer, e.g. for a texture that copies another block. */
    fun layerOf(block: Block, meta: Int, side: Int = TOP): IconLayer? =
        runCatching { block.getIcon(side, meta) }.getOrNull()?.let(::layerOf)

    /** Composites layers bottom-up by coverage; null when nothing is visible. */
    fun compose(layers: List<IconLayer>): Int? {
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var alpha = 0.0
        for (layer in layers) {
            val a = layer.coverage / 255.0
            if (a <= 0.0) continue
            r = r * (1 - a) + (layer.argb shr 16 and 255) * a
            g = g * (1 - a) + (layer.argb shr 8 and 255) * a
            b = b * (1 - a) + (layer.argb and 255) * a
            alpha = alpha + a * (1 - alpha)
        }
        if (alpha * 255 < OPAQUE_ALPHA) return null
        return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    /** A provider's colour, cached under its own key until the atlas is stitched again. */
    fun cached(key: String, compute: () -> BlockColor): BlockColor = byBlock.getOrPut(key, compute)

    fun blockColor(argb: Int, tint: Tint, decoration: Boolean, variant: String?, detail: String? = null): BlockColor =
        if (argb == ChunkColumns.TRANSPARENT) transparent else BlockColor(argb, tint, decoration, variant, detail)

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
            val frame = minOf(side, image.height)
            // Average in linear light: a dark texture with a few bright lines then reads lighter
            // than a flat dark one, the way the eye sees it, instead of collapsing to the same
            // grey.
            // Transparent texels are skipped and translucent ones weigh by their alpha, so a
            // flower is its petals' colour and a leaf block its leaves', not a blend with nothing.
            var r = 0.0
            var g = 0.0
            var b = 0.0
            var alpha = 0L
            var weight = 0.0
            for (y in 0 until frame) for (x in 0 until side) {
                val pixel = image.getRGB(x, y)
                val a = pixel ushr 24
                alpha += a
                if (a == 0) continue
                val w = a / 255.0
                weight += w
                r += TO_LINEAR[pixel shr 16 and 255] * w
                g += TO_LINEAR[pixel shr 8 and 255] * w
                b += TO_LINEAR[pixel and 255] * w
            }
            val texels = side * frame
            if (weight == 0.0 || texels == 0) return 0L
            val argb = (0xFF shl 24) or (toSrgb(r / weight) shl 16) or (toSrgb(g / weight) shl 8) or toSrgb(b / weight)
            (argb.toLong() and 0xFFFFFFFFL shl 8) or (alpha / texels)
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

    private val TO_LINEAR =
        DoubleArray(256) { value ->
            val c = value / 255.0
            if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }

    private fun toSrgb(linear: Double): Int {
        val c =
            if (linear <= 0.0031308) linear * 12.92 else 1.055 * Math.pow(linear, 1 / 2.4) - 0.055
        return (c * 255.0 + 0.5).toInt().coerceIn(0, 255)
    }
}
