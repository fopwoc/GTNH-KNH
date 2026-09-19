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
import net.minecraftforge.client.event.TextureStitchEvent
import net.minecraftforge.common.MinecraftForge
import org.apache.logging.log4j.LogManager

/**
 * One color per block and metadata, averaged from the block's top texture, computed on first use
 * and forgotten whenever Forge re-stitches the block atlas (so resource packs are honoured).
 *
 * The texture is decoded from the resource manager rather than read off the stitched sprite,
 * because the atlas drops sprite pixel data right after upload — before the stitch event fires.
 * Only full, opaque cubes count as a surface; slabs, stairs, fences, plants, torches, glass and the
 * like are [ChunkColumns.TRANSPARENT] so the map reads the solid block underneath. Liquids are the
 * one exception: they stay visible and get depth shading. Blocks without a readable texture fall
 * back to their vanilla map color.
 */
@SideOnly(Side.CLIENT)
object BlockColors {
    /**
     * A block's untinted average color and whether the biome's grass color is applied over it at
     * render time. Tintable is a property of the block (its texture is greyscale by design and the
     * game colors it per biome), never inferred from the color.
     */
    class BlockColor(val argb: Int, val tintable: Boolean) {
        val isTransparent: Boolean
            get() = argb == ChunkColumns.TRANSPARENT
    }

    /** Every distinct color in the game, split by band; the input for a world palette. */
    class Colors(val plain: Set<Int>, val tintable: Set<Int>)

    private const val METAS = 16
    private const val TOP = 1
    private const val OPAQUE_ALPHA = 64
    private const val WHITE = 0xFFFFFF
    private val logger = LogManager.getLogger(BlockColors::class.java)
    private var registered = false
    private val byBlock = ConcurrentHashMap<String, BlockColor>()
    private val byIcon = ConcurrentHashMap<String, Int>()
    private val transparent = BlockColor(ChunkColumns.TRANSPARENT, false)

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

    fun of(block: Block, meta: Int): BlockColor =
        byBlock.getOrPut("${Block.getIdFromBlock(block)}:${meta and 15}") {
            compute(block, meta and 15)
        }

    /** Every distinct opaque color of every registered block, by band. */
    fun distinctColors(): Colors {
        val start = System.nanoTime()
        val plain = HashSet<Int>()
        val tintable = HashSet<Int>()
        val blocks =
            Block.blockRegistry.keys.mapNotNull { Block.blockRegistry.getObject(it) as? Block }
        for (block in blocks) for (meta in 0 until METAS) {
            val color = of(block, meta)
            if (color.isTransparent) continue
            if (color.tintable) tintable += color.argb else plain += color.argb
        }
        logger.info(
            "Block colors: {} blocks, {} plain and {} tintable colors from {} textures in {} ms",
            blocks.size,
            plain.size,
            tintable.size,
            byIcon.size,
            (System.nanoTime() - start) / 1_000_000,
        )
        return Colors(plain, tintable)
    }

    @SubscribeEvent
    fun onStitch(event: TextureStitchEvent.Post) {
        if (event.map.textureType != 0) return
        byBlock.clear()
        byIcon.clear()
        version++
    }

    /** Why a block classifies the way it does; for a debug command. */
    fun describe(block: Block, meta: Int): String {
        val icon = runCatching { block.getIcon(TOP, meta) }.getOrNull()
        return buildString {
            append(Block.blockRegistry.getNameForObject(block)).append(':').append(meta)
            append(" fullCube=").append(isFullCube(block))
            append(" liquid=").append(block.material.isLiquid)
            append(" icon=").append(icon?.iconName ?: "none")
            append(" texture=")
                .append(icon?.let { textureAverage(it) }?.let { "%08X".format(it) } ?: "none")
            val color = of(block, meta)
            append(" color=").append("%08X".format(color.argb))
            append(" tintable=").append(color.tintable)
        }
    }

    /**
     * Untinted color plus the tint flag. A block is tintable when the game itself colors it per
     * biome — it reports a non-white render color (grass, tall grass, vines) or is foliage; its
     * texture is then stored as-is (greyscale) and the biome's grass color is applied at render.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun compute(block: Block, meta: Int): BlockColor {
        if (!block.material.isLiquid && !isFullCube(block)) return transparent
        val tintable =
            block.material === Material.leaves ||
                runCatching { block.getRenderColor(meta) != WHITE }.getOrDefault(false)
        val textured =
            try {
                block.getIcon(TOP, meta)?.let(::textureAverage)
            } catch (failure: Exception) {
                // Blocks index icon arrays by metadata and throw on values they never use.
                null
            }
        val color = textured ?: fallback(block, meta)
        return if (color == ChunkColumns.TRANSPARENT) transparent else BlockColor(color, tintable)
    }

    /** Static bounds fill the whole block and it renders as a plain cube; graphics-setting free. */
    private fun isFullCube(block: Block): Boolean =
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

    private fun fallback(block: Block, meta: Int): Int {
        val mapColor =
            runCatching { block.getMapColor(meta) }.getOrNull() ?: return ChunkColumns.TRANSPARENT
        return if (mapColor === MapColor.airColor) ChunkColumns.TRANSPARENT
        else (0xFF shl 24) or mapColor.colorValue
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
