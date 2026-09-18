package io.github.fopwoc.mods.framework.world.minecraft

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.world.ChunkColumns
import net.minecraft.block.Block
import net.minecraft.block.material.MapColor
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraftforge.client.event.TextureStitchEvent
import net.minecraftforge.common.MinecraftForge
import org.apache.logging.log4j.LogManager

/**
 * One color per block and metadata, averaged from the block's top texture each time Forge stitches
 * the block atlas (so resource packs are honoured). Blocks whose texture is mostly transparent are
 * [ChunkColumns.TRANSPARENT]; blocks without a usable icon fall back to their vanilla map color.
 */
@SideOnly(Side.CLIENT)
object BlockColors {
    private const val METAS = 16
    private const val TOP = 1
    private const val OPAQUE_ALPHA = 64
    private val logger = LogManager.getLogger(BlockColors::class.java)
    private var registered = false

    @Volatile private var table = IntArray(0)

    /** Increments on every rebuild so palettes and caches can notice a resource pack change. */
    @Volatile
    var version = 0
        private set

    fun register() {
        if (registered) return
        registered = true
        MinecraftForge.EVENT_BUS.register(this)
    }

    fun of(block: Block, meta: Int): Int {
        val index = Block.getIdFromBlock(block) * METAS + (meta and 15)
        val colors = table
        return if (index in colors.indices) colors[index] else fallback(block, meta)
    }

    /** Every distinct opaque color currently in the table; the input for a world palette. */
    fun distinctColors(): Set<Int> = table.filterTo(HashSet()) { it != ChunkColumns.TRANSPARENT }

    @SubscribeEvent
    fun onStitch(event: TextureStitchEvent.Post) {
        if (event.map.textureType != 0) return
        rebuild(event.map)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun rebuild(atlas: TextureMap) {
        val start = System.nanoTime()
        val blocks =
            Block.blockRegistry.keys.mapNotNull { Block.blockRegistry.getObject(it) as? Block }
        val highest = blocks.maxOfOrNull(Block::getIdFromBlock) ?: 0
        val colors = IntArray((highest + 1) * METAS)
        var averaged = 0
        for (block in blocks) {
            val base = Block.getIdFromBlock(block) * METAS
            for (meta in 0 until METAS) {
                colors[base + meta] =
                    try {
                        val icon = block.getIcon(TOP, meta) as? TextureAtlasSprite
                        val texel = icon?.let(::averageTexel)
                        if (texel != null) {
                            averaged++
                            tint(texel, block.getRenderColor(meta))
                        } else fallback(block, meta)
                    } catch (failure: Exception) {
                        // Blocks index icon arrays by metadata and throw on values they never use.
                        logger.debug(
                            "No top icon for {} meta {}: {}",
                            block,
                            meta,
                            failure.toString(),
                        )
                        fallback(block, meta)
                    }
            }
        }
        table = colors
        version++
        logger.info(
            "Block colors: {} blocks, {} textured entries in {} ms (atlas {})",
            blocks.size,
            averaged,
            (System.nanoTime() - start) / 1_000_000,
            atlas.textureType,
        )
    }

    /** Average of the opaque texels of the first frame at mip 0; null when mostly see-through. */
    private fun averageTexel(icon: TextureAtlasSprite): Int? {
        if (icon.frameCount <= 0) return null
        val pixels = icon.getFrameTextureData(0).firstOrNull() ?: return null
        if (pixels.isEmpty()) return null
        var r = 0L
        var g = 0L
        var b = 0L
        var alpha = 0L
        var opaque = 0
        for (pixel in pixels) {
            val a = pixel ushr 24
            alpha += a
            if (a == 0) continue
            opaque++
            r += pixel shr 16 and 255
            g += pixel shr 8 and 255
            b += pixel and 255
        }
        if (opaque == 0 || alpha / pixels.size < OPAQUE_ALPHA) return ChunkColumns.TRANSPARENT
        return (0xFF shl 24) or
            ((r / opaque).toInt() shl 16) or
            ((g / opaque).toInt() shl 8) or
            (b / opaque).toInt()
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
}
