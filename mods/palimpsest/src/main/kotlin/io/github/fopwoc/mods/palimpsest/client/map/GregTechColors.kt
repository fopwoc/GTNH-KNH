package io.github.fopwoc.mods.palimpsest.client.map

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import java.lang.reflect.Method
import net.minecraft.block.Block
import net.minecraft.util.IIcon
import net.minecraft.world.IBlockAccess
import net.minecraftforge.common.util.ForgeDirection
import org.apache.logging.log4j.LogManager

/**
 * Colours for GregTech machines, whose look lives in the tile entity: the machine's texture
 * layers for the top face — casing, overlay, dye tint — composited the way the renderer stacks
 * them, asked with `active = false` so a running machine never changes colour on the map. GregTech
 * is reached by reflection, so the map neither depends on it nor breaks without it.
 */
@SideOnly(Side.CLIENT)
object GregTechColors : BlockColors.Provider {
    private val logger = LogManager.getLogger(GregTechColors::class.java)

    private class Api(loader: ClassLoader) {
        val gregTechTileEntity: Class<*> = loader.loadClass("gregtech.api.interfaces.tileentity.IGregTechTileEntity")
        val metaTileEntity: Class<*> = loader.loadClass("gregtech.api.interfaces.metatileentity.IMetaTileEntity")
        val getMetaTileEntity: Method = gregTechTileEntity.getMethod("getMetaTileEntity")
        val getMetaTileID: Method = gregTechTileEntity.getMethod("getMetaTileID")
        val getFrontFacing: Method = gregTechTileEntity.getMethod("getFrontFacing")
        val getColorization: Method = gregTechTileEntity.getMethod("getColorization")
        val getTexture: Method =
            metaTileEntity.getMethod(
                "getTexture",
                gregTechTileEntity,
                ForgeDirection::class.java,
                ForgeDirection::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
        val rendered: Class<*> = loader.loadClass("gregtech.common.render.GTRenderedTexture")
        val renderedContainer = rendered.getDeclaredField("mIconContainer").also { it.isAccessible = true }
        val renderedRgba: Method = rendered.getMethod("getRGBA")
        val multi: Class<*> = loader.loadClass("gregtech.common.render.GTMultiTextureRender")
        val multiTextures = multi.getDeclaredField("mTextures").also { it.isAccessible = true }
        val sided: Class<*> = loader.loadClass("gregtech.common.render.GTSidedTextureRender")
        val sidedTextures = sided.getDeclaredField("mTextures").also { it.isAccessible = true }
        val copied: Class<*> = loader.loadClass("gregtech.common.render.GTCopiedBlockTextureRender")
        val copiedBlock: Method = copied.getMethod("getBlock")
        val copiedMeta: Method = copied.getMethod("getMeta")
        val container: Class<*> = loader.loadClass("gregtech.api.interfaces.IIconContainer")
        val containerIcon: Method = container.getMethod("getIcon")
        val containerOverlay: Method = container.getMethod("getOverlayIcon")
    }

    private val api: Api? =
        runCatching { Api(GregTechColors::class.java.classLoader) }
            .onFailure { logger.info("GregTech not found; machines take their block's texture ({})", it.toString()) }
            .getOrNull()

    fun register() {
        if (api != null) BlockColors.registerProvider(this)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun colorOf(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block, meta: Int): BlockColors.BlockColor? {
        val api = api ?: return null
        val tile = world.getTileEntity(x, y, z) ?: return null
        if (!api.gregTechTileEntity.isInstance(tile)) return null
        return try {
            val machine = api.getMetaTileEntity.invoke(tile) ?: return null
            val id = api.getMetaTileID.invoke(tile) as Int
            val facing = api.getFrontFacing.invoke(tile) as ForgeDirection
            val color = (api.getColorization.invoke(tile) as Byte).toInt()
            val frontUp = facing == ForgeDirection.UP
            val key = "${Block.getIdFromBlock(block)}:$meta@mte$id/c$color${if (frontUp) "/up" else ""}"
            BlockColors.cached(key) {
                val textures = api.getTexture.invoke(machine, tile, ForgeDirection.UP, facing, color, false, false) as? Array<*>
                val layers = ArrayList<BlockColors.IconLayer>()
                textures?.forEach { collect(api, it, layers) }
                val argb = BlockColors.compose(layers) ?: 0
                BlockColors.blockColor(argb, tintable = false, decoration = false, variant = "mte$id/c$color${if (frontUp) "/up" else ""}")
            }
        } catch (failure: Exception) {
            logger.debug("GregTech texture of {} at {},{},{} unreadable: {}", block, x, y, z, failure.toString())
            null
        }
    }

    /** Flattens a texture into layers, bottom first, the way GregTech renders them. */
    private fun collect(api: Api, texture: Any?, out: MutableList<BlockColors.IconLayer>) {
        when {
            texture == null -> Unit
            api.multi.isInstance(texture) -> (api.multiTextures.get(texture) as Array<*>).forEach { collect(api, it, out) }
            api.sided.isInstance(texture) -> collect(api, (api.sidedTextures.get(texture) as Array<*>).getOrNull(ForgeDirection.UP.ordinal), out)
            api.copied.isInstance(texture) -> {
                val block = api.copiedBlock.invoke(texture) as? Block ?: return
                BlockColors.layerOf(block, api.copiedMeta.invoke(texture) as Int)?.let(out::add)
            }
            api.rendered.isInstance(texture) -> {
                val container = api.renderedContainer.get(texture) ?: return
                val rgba = api.renderedRgba.invoke(texture) as? ShortArray
                (api.containerIcon.invoke(container) as? IIcon)?.let(BlockColors::layerOf)?.let { out += tint(it, rgba) }
                (api.containerOverlay.invoke(container) as? IIcon)?.let(BlockColors::layerOf)?.let(out::add)
            }
        }
    }

    /** GregTech modulates the base icon by the machine's dye colour; overlays stay as drawn. */
    private fun tint(layer: BlockColors.IconLayer, rgba: ShortArray?): BlockColors.IconLayer {
        if (rgba == null || rgba.size < 3) return layer
        val r = (layer.argb shr 16 and 255) * rgba[0] / 255
        val g = (layer.argb shr 8 and 255) * rgba[1] / 255
        val b = (layer.argb and 255) * rgba[2] / 255
        return BlockColors.IconLayer((0xFF shl 24) or (r shl 16) or (g shl 8) or b, layer.coverage)
    }
}
