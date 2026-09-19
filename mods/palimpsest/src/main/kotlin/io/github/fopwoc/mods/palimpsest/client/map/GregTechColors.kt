package io.github.fopwoc.mods.palimpsest.client.map

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import java.lang.reflect.Method
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.util.IIcon
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection
import org.apache.logging.log4j.LogManager

/**
 * Colours for GregTech machines, whose look lives in the tile entity: the machine's texture layers
 * for the top face — casing, overlay, dye tint — composited the way the renderer stacks them, asked
 * with `active = false` so a running machine never changes colour on the map. GregTech is reached
 * by reflection, so the map neither depends on it nor breaks without it.
 */
@SideOnly(Side.CLIENT)
object GregTechColors : BlockColors.Provider {
    private val logger = LogManager.getLogger(GregTechColors::class.java)
    private const val TOP = 1
    private const val OVERLAY_EMPHASIS = 2.5
    private const val OVERLAY_MAX = 200
    /** A base icon this opaque is a casing face, not a marking. */
    private const val OPAQUE_BASE = 200
    /** Neighbour rings, nearest first: beside, diagonal beside, then the layers above and below. */
    private val RINGS: List<List<Triple<Int, Int, Int>>> =
        listOf(
            listOf(Triple(1, 0, 0), Triple(-1, 0, 0), Triple(0, 0, 1), Triple(0, 0, -1)),
            listOf(Triple(1, 0, 1), Triple(-1, 0, 1), Triple(1, 0, -1), Triple(-1, 0, -1)),
            (-1..1).flatMap { dx -> (-1..1).map { dz -> Triple(dx, -1, dz) } } +
                (-1..1).flatMap { dx -> (-1..1).map { dz -> Triple(dx, 1, dz) } },
        )

    private class Api(loader: ClassLoader) {
        val gregTechTileEntity: Class<*> =
            loader.loadClass("gregtech.api.interfaces.tileentity.IGregTechTileEntity")
        val metaTileEntity: Class<*> =
            loader.loadClass("gregtech.api.interfaces.metatileentity.IMetaTileEntity")
        /** Hatches, buses, mufflers, maintenance: machines that show their multiblock's casing. */
        val casingProvider: Class<*> =
            loader.loadClass("gregtech.api.interfaces.tileentity.ICasingTextureProvider")
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
        val renderedContainer =
            rendered.getDeclaredField("mIconContainer").also { it.isAccessible = true }
        val renderedRgba: Method = rendered.getMethod("getRGBA")
        val multi: Class<*> = loader.loadClass("gregtech.common.render.GTMultiTextureRender")
        val multiTextures = multi.getDeclaredField("mTextures").also { it.isAccessible = true }
        val sided: Class<*> = loader.loadClass("gregtech.common.render.GTSidedTextureRender")
        val sidedTextures = sided.getDeclaredField("mTextures").also { it.isAccessible = true }
        val copied: Class<*> = loader.loadClass("gregtech.common.render.GTCopiedBlockTextureRender")
        val copiedBlock: Method = copied.getMethod("getBlock")
        val copiedMeta: Method = copied.getMethod("getMeta")
        /** Which face of the copied block is shown; 6 means all faces alike. */
        val copiedSide = copied.getDeclaredField("mSide").also { it.isAccessible = true }
        /**
         * Blocks that render through GregTech's texture layers without a tile entity: frame boxes.
         */
        val texturedBlock: Class<*> = loader.loadClass("gregtech.api.interfaces.IBlockWithTextures")
        val blockTextures: Method =
            texturedBlock.getMethod(
                "getTextures",
                IBlockAccess::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
        val container: Class<*> = loader.loadClass("gregtech.api.interfaces.IIconContainer")
        val containerIcon: Method = container.getMethod("getIcon")
        val containerOverlay: Method = container.getMethod("getOverlayIcon")
    }

    private val api: Api? =
        runCatching { Api(GregTechColors::class.java.classLoader) }
            .onFailure {
                logger.info(
                    "GregTech not found; machines take their block's texture ({})",
                    it.toString(),
                )
            }
            .getOrNull()

    fun register() {
        if (api != null) BlockColors.registerProvider(this)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun colorOf(
        world: IBlockAccess,
        x: Int,
        y: Int,
        z: Int,
        block: Block,
        meta: Int,
    ): BlockColors.BlockColor? {
        val api = api ?: return null
        if (api.texturedBlock.isInstance(block))
            return texturedBlock(api, world, x, y, z, block, meta)
        val tile = world.getTileEntity(x, y, z) ?: return null
        if (!api.gregTechTileEntity.isInstance(tile)) return null
        return try {
            val machine = api.getMetaTileEntity.invoke(tile) ?: return null
            val id = api.getMetaTileID.invoke(tile) as Int
            val facing = api.getFrontFacing.invoke(tile) as ForgeDirection
            val color = (api.getColorization.invoke(tile) as Byte).toInt()
            val frontUp = facing == ForgeDirection.UP
            // A hatch shows the wall it sits in and is judged only with every neighbour loaded,
            // else one on a chunk edge would be recorded against the wrong wall and flip later.
            val hatch = api.casingProvider.isInstance(machine)
            if (hatch && !neighbourhoodLoaded(world, x, y, z)) return null
            val casing = if (hatch) casingAround(world, x, y, z, block) else null
            val key =
                "${Block.getIdFromBlock(block)}:$meta@mte$id/c$color${if (frontUp) "/up" else ""}${casing?.let { "/in${it.key}" } ?: ""}"
            BlockColors.cached(key) {
                val textures =
                    api.getTexture.invoke(
                        machine,
                        tile,
                        ForgeDirection.UP,
                        facing,
                        color,
                        false,
                        false,
                    ) as? Array<*>
                // A hatch mimics the casing wall it sits in, so the casing next to it is the base;
                // GregTech's own icon for the machine follows transient texture state and is only
                // the fallback for a machine standing alone.
                val layers = ArrayList<BlockColors.IconLayer>()
                val names = ArrayList<String>()
                if (casing != null) {
                    layers += BlockColors.IconLayer(casing.color.argb, 255)
                    names += "casing:" + casing.key
                    textures?.forEach { collect(api, it, layers, names, overlaysOnly = true) }
                } else {
                    // A machine of its own: its whole texture stack, casing tier and all.
                    textures?.forEach { collect(api, it, layers, names, overlaysOnly = false) }
                }
                val argb = BlockColors.compose(emphasiseMarkings(layers)) ?: 0
                val detail =
                    (names
                            .filter { !it.startsWith("!") }
                            .zip(layers)
                            .map { (name, layer) ->
                                "$name=%06X@${layer.coverage}".format(layer.argb and 0xFFFFFF)
                            } + names.filter { it.startsWith("!") })
                        .joinToString(" ")
                BlockColors.blockColor(
                    argb,
                    tint = BlockColors.Tint.NONE,
                    decoration = false,
                    variant =
                        "mte$id/c$color${if (frontUp) "/up" else ""}${casing?.let { "/in${it.key}" } ?: ""}",
                    detail = detail.ifEmpty { "none" },
                )
            }
        } catch (failure: Exception) {
            logger.debug(
                "GregTech texture of {} at {},{},{} unreadable: {}",
                block,
                x,
                y,
                z,
                failure.toString(),
            )
            null
        }
    }

    private class Casing(val key: String, val color: BlockColors.BlockColor)

    /**
     * The most common full, non-machine block beside or under a machine: the casing wall it is part
     * of.
     */
    private fun casingAround(world: IBlockAccess, x: Int, y: Int, z: Int, machine: Block): Casing? {
        for (ring in RINGS) {
            val counts = HashMap<String, Pair<Int, BlockColors.BlockColor>>()
            for ((dx, dy, dz) in ring) {
                val casing = casingAt(world, x + dx, y + dy, z + dz, machine) ?: continue
                counts.merge(casing.key, 1 to casing.color) { old, new ->
                    (old.first + new.first) to old.second
                }
            }
            val best =
                counts.entries.maxWithOrNull(compareBy({ it.value.first }, { it.key })) ?: continue
            return Casing(best.key, best.value.second)
        }
        return null
    }

    /** Whether every neighbour the casing search looks at is in a loaded chunk. */
    private fun neighbourhoodLoaded(world: IBlockAccess, x: Int, y: Int, z: Int): Boolean =
        world !is World ||
            RINGS.all { ring -> ring.all { (dx, _, dz) -> world.blockExists(x + dx, y, z + dz) } }

    /**
     * The block at a position if it can pass as a casing: full, not air, not a machine, with a
     * colour.
     */
    private fun casingAt(world: IBlockAccess, x: Int, y: Int, z: Int, machine: Block): Casing? {
        val block = world.getBlock(x, y, z)
        if (block === machine || block.material === Material.air || !BlockColors.isFullCube(block))
            return null
        if (api?.gregTechTileEntity?.isInstance(world.getTileEntity(x, y, z)) == true) return null
        val meta = world.getBlockMetadata(x, y, z)
        val color = BlockColors.of(world, x, y, z, block, meta)
        if (color.isTransparent) return null
        return Casing(
            "${Block.blockRegistry.getNameForObject(block)}:$meta${color.variant?.let { "@$it" } ?: ""}",
            color,
        )
    }

    /** A block whose look is GregTech texture layers keyed by its metadata, such as a frame box. */
    @Suppress("TooGenericExceptionCaught", "LongParameterList")
    private fun texturedBlock(
        api: Api,
        world: IBlockAccess,
        x: Int,
        y: Int,
        z: Int,
        block: Block,
        meta: Int,
    ): BlockColors.BlockColor? =
        try {
            BlockColors.cached("${Block.getIdFromBlock(block)}:$meta@textured") {
                val sides = api.blockTextures.invoke(block, world, x, y, z) as? Array<*>
                val layers = ArrayList<BlockColors.IconLayer>()
                val names = ArrayList<String>()
                (sides?.getOrNull(ForgeDirection.UP.ordinal) as? Array<*>)?.forEach {
                    collect(api, it, layers, names, overlaysOnly = false)
                }
                // An ore is its material to the map, not the stone it sits in: the vein layer
                // dominates.
                val argb =
                    BlockColors.compose(emphasiseMarkings(layers, atLeast = OVERLAY_MAX)) ?: 0
                BlockColors.blockColor(
                    argb,
                    BlockColors.Tint.NONE,
                    decoration = !BlockColors.isFullCube(block),
                    variant = "textured",
                    detail = describe(names, layers),
                )
            }
        } catch (failure: Exception) {
            logger.debug(
                "GregTech textures of {} at {},{},{} unreadable: {}",
                block,
                x,
                y,
                z,
                failure.toString(),
            )
            null
        }

    private fun describe(names: List<String>, layers: List<BlockColors.IconLayer>): String =
        (names
                .filter { !it.startsWith("!") }
                .zip(layers)
                .map { (name, layer) ->
                    "$name=%06X@${layer.coverage}".format(layer.argb and 0xFFFFFF)
                } + names.filter { it.startsWith("!") })
            .joinToString(" ")
            .ifEmpty { "none" }

    /** Flattens a texture into layers, bottom first, the way GregTech renders them. */
    private fun collect(
        api: Api,
        texture: Any?,
        out: MutableList<BlockColors.IconLayer>,
        names: MutableList<String>,
        overlaysOnly: Boolean,
    ) {
        when {
            texture == null -> Unit
            api.multi.isInstance(texture) ->
                (api.multiTextures.get(texture) as Array<*>).forEach {
                    collect(api, it, out, names, overlaysOnly)
                }
            api.sided.isInstance(texture) ->
                collect(
                    api,
                    (api.sidedTextures.get(texture) as Array<*>).getOrNull(
                        ForgeDirection.UP.ordinal
                    ),
                    out,
                    names,
                    overlaysOnly,
                )
            api.copied.isInstance(texture) && overlaysOnly -> Unit
            api.copied.isInstance(texture) -> {
                val block = api.copiedBlock.invoke(texture) as? Block ?: return
                val meta = api.copiedMeta.invoke(texture) as Int
                val side =
                    (api.copiedSide.get(texture) as Byte).toInt().let {
                        if (it in 0..5) it else TOP
                    }
                val layer = BlockColors.layerOf(block, meta, side)
                if (layer != null) {
                    out += layer
                    names += "copy(${Block.blockRegistry.getNameForObject(block)}:$meta/$side)"
                } else
                    names +=
                        "!copy(${Block.blockRegistry.getNameForObject(block)}:$meta)=unreadable"
            }
            api.rendered.isInstance(texture) -> {
                val container = api.renderedContainer.get(texture) ?: return
                val rgba = api.renderedRgba.invoke(texture) as? ShortArray
                val icon = api.containerIcon.invoke(container) as? IIcon
                // In overlay mode an opaque base icon is a casing and is left out; a sparse one is
                // a
                // marking (the muffler's hole is a rendered texture whose base icon is the mark).
                val base =
                    icon?.let(BlockColors::layerOf)?.takeIf {
                        !overlaysOnly || it.coverage < OPAQUE_BASE
                    }
                if (base != null && icon != null) {
                    out += tint(base, rgba)
                    names += icon.iconName
                } else if (!overlaysOnly) names += "!${icon?.iconName}=unreadable"
                val overlay = api.containerOverlay.invoke(container) as? IIcon
                val overlayLayer = overlay?.let(BlockColors::layerOf)
                if (overlayLayer != null) {
                    out += overlayLayer
                    names += "overlay:" + overlay.iconName
                } else if (overlay != null) names += "!overlay:${overlay.iconName}=unreadable"
            }
            else -> names += "!" + texture.javaClass.simpleName + "=unsupported"
        }
    }

    /**
     * The first layer is the base; every later layer that is not opaque is a marking — an ore vein,
     * a hatch symbol — of a few texels that needs weight to read at one pixel per block.
     */
    private fun emphasiseMarkings(
        layers: List<BlockColors.IconLayer>,
        atLeast: Int = 0,
    ): List<BlockColors.IconLayer> = layers.mapIndexed { index, layer ->
        // An empty layer (an overlay slot GregTech left blank) stays empty.
        if (index == 0 || layer.coverage == 0 || layer.coverage >= OPAQUE_BASE) layer
        else
            BlockColors.IconLayer(
                layer.argb,
                (layer.coverage * OVERLAY_EMPHASIS).toInt().coerceIn(atLeast, OVERLAY_MAX),
            )
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
