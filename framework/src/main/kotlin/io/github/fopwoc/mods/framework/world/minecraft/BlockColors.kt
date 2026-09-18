package io.github.fopwoc.mods.framework.world.minecraft

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.world.ChunkColumns
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import net.minecraft.block.Block
import net.minecraft.block.material.MapColor
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
    private const val METAS = 16
    private const val TOP = 1
    private const val OPAQUE_ALPHA = 64
    private val logger = LogManager.getLogger(BlockColors::class.java)
    private var registered = false
    private val byBlock = ConcurrentHashMap<String, Int>()
    private val byIcon = ConcurrentHashMap<String, Int>()

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

    fun of(block: Block, meta: Int): Int =
        byBlock.getOrPut("${Block.getIdFromBlock(block)}:${meta and 15}") {
            compute(block, meta and 15)
        }

    /** Every distinct opaque color of every registered block; the input for a world palette. */
    fun distinctColors(): Set<Int> {
        val start = System.nanoTime()
        val colors = HashSet<Int>()
        val blocks =
            Block.blockRegistry.keys.mapNotNull { Block.blockRegistry.getObject(it) as? Block }
        for (block in blocks) for (meta in 0 until METAS) {
            val color = of(block, meta)
            if (color != ChunkColumns.TRANSPARENT) colors += color
        }
        logger.info(
            "Block colors: {} blocks, {} distinct colors from {} textures in {} ms",
            blocks.size,
            colors.size,
            byIcon.size,
            (System.nanoTime() - start) / 1_000_000,
        )
        return colors
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
            append(" color=").append("%08X".format(of(block, meta)))
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun compute(block: Block, meta: Int): Int {
        if (!block.material.isLiquid && !isFullCube(block)) return ChunkColumns.TRANSPARENT
        val textured =
            try {
                block.getIcon(TOP, meta)?.let(::textureAverage)
            } catch (failure: Exception) {
                // Blocks index icon arrays by metadata and throw on values they never use.
                null
            }
        return textured?.let { tint(it, block.getRenderColor(meta)) } ?: fallback(block, meta)
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

    private fun tint(color: Int, multiplier: Int): Int {
        if (color == ChunkColumns.TRANSPARENT || multiplier == 0xFFFFFF) return color
        val r = (color shr 16 and 255) * (multiplier shr 16 and 255) / 255
        val g = (color shr 8 and 255) * (multiplier shr 8 and 255) / 255
        val b = (color and 255) * (multiplier and 255) / 255
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
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
