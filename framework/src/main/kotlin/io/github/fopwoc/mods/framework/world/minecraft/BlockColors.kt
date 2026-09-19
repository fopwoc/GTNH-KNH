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
 * but air is a surface: plants, slabs, frames and glass draw with the colour of their opaque
 * texels, and only a texture with almost no opaque texels (a torch, string) is see-through. A
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
    class BlockColor(val argb: Int, val tintable: Boolean, val decoration: Boolean, val variant: String?) {
        val isTransparent: Boolean
            get() = argb == ChunkColumns.TRANSPARENT
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
    private val transparent = BlockColor(ChunkColumns.TRANSPARENT, false, false, null)

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
        val icon = worldIcon(world, x, y, z, block) ?: staticIcon(block, meta)
        // The full metadata: EndlessIDs gives blocks 16 bits of it, and GregTech ores use them.
        return byBlock.getOrPut("${Block.getIdFromBlock(block)}:$meta:${icon?.iconName}") {
            compute(block, meta, icon, variant = icon?.iconName?.takeIf { it != staticIcon(block, meta)?.iconName })
        }
    }

    /** The colour of a block as its static icon shows it, for tools without a world position. */
    fun of(block: Block, meta: Int): BlockColor {
        val icon = staticIcon(block, meta)
        return byBlock.getOrPut("${Block.getIdFromBlock(block)}:$meta:${icon?.iconName}") { compute(block, meta, icon, null) }
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
            append(" tintable=").append(color.tintable)
            append(" decoration=").append(color.decoration)
        }
    }

    /**
     * Untinted color plus the flags. A block is tintable when the game itself colors it per
     * biome — it reports a non-white render color (grass, tall grass, vines) or is foliage; its
     * texture is then stored as-is (greyscale) and the biome's grass color is applied at render.
     */
    private fun compute(block: Block, meta: Int, icon: IIcon?, variant: String?): BlockColor {
        if (block.material === Material.air) return transparent
        val tintable =
            block.material === Material.leaves ||
                runCatching { block.getRenderColor(meta) != WHITE }.getOrDefault(false)
        val textured = icon?.let(::textureAverage)
        val color = textured ?: fallback(block, meta)
        if (color == ChunkColumns.TRANSPARENT) return transparent
        return BlockColor(color, tintable, decoration = !block.material.isLiquid && !isFullCube(block), variant = variant)
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

    /** Average of the opaque texels of the texture's first frame; null when unreadable. */
    private fun textureAverage(icon: IIcon): Int? {
        val name = icon.iconName ?: return null
        return byIcon.getOrPut(name) { decodeAverage(name) ?: MISSING }.takeIf { it != MISSING }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun decodeAverage(iconName: String): Int? {
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
            var r = 0.0
            var g = 0.0
            var b = 0.0
            var alpha = 0L
            var opaque = 0
            for (y in 0 until frame) for (x in 0 until side) {
                val pixel = image.getRGB(x, y)
                val a = pixel ushr 24
                alpha += a
                if (a == 0) continue
                opaque++
                r += TO_LINEAR[pixel shr 16 and 255]
                g += TO_LINEAR[pixel shr 8 and 255]
                b += TO_LINEAR[pixel and 255]
            }
            val texels = side * frame
            if (opaque == 0 || texels == 0 || alpha / texels < OPAQUE_ALPHA)
                ChunkColumns.TRANSPARENT
            else
                (0xFF shl 24) or
                    (toSrgb(r / opaque) shl 16) or
                    (toSrgb(g / opaque) shl 8) or
                    toSrgb(b / opaque)
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
