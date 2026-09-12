package io.github.fopwoc.mods.hotspot.server

import io.github.fopwoc.mods.hotspot.server.profiler.RawTileEntitySample
import java.lang.reflect.Method
import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.common.DimensionManager
import org.apache.logging.log4j.LogManager

/**
 * Best-effort human name for a profiled tile entity: the GT machine name when the tile entity is a
 * GregTech one, otherwise the block's item name, otherwise the class name. Looks the tile entity up
 * in the live world, so it must run on the server thread.
 */
object TileEntityNameResolver {
  private val logger = LogManager.getLogger(TileEntityNameResolver::class.java)
  private val gregTechAccessors = HashMap<Class<*>, GregTechAccessor?>()

  fun resolve(sample: RawTileEntitySample): String {
    val fallback = sample.className.ifEmpty { "TileEntity" }
    val world = DimensionManager.getWorld(sample.dimensionId) ?: return fallback
    if (!world.blockExists(sample.x, sample.y, sample.z)) {
      return fallback
    }
    val tileEntity = world.getTileEntity(sample.x, sample.y, sample.z) ?: return fallback
    gregTechName(tileEntity)?.let {
      return it
    }
    val block = world.getBlock(sample.x, sample.y, sample.z)
    val meta = world.getBlockMetadata(sample.x, sample.y, sample.z)
    return itemName(block, meta) ?: Block.blockRegistry.getNameForObject(block) ?: fallback
  }

  private fun itemName(block: Block, meta: Int): String? {
    val item = Item.getItemFromBlock(block) ?: return null
    return runCatching { ItemStack(item, 1, block.damageDropped(meta)).displayName }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && !it.endsWith(".name") }
  }

  /** GT5u: `BaseMetaTileEntity.getMetaTileEntity().getLocalName()`; resolved once per class. */
  private fun gregTechName(tileEntity: TileEntity): String? {
    val accessor =
        gregTechAccessors.getOrPut(tileEntity.javaClass) {
          GregTechAccessor.of(tileEntity.javaClass)
        } ?: return null
    return runCatching { accessor.localName(tileEntity) }
        .onFailure { logger.debug("GT name lookup failed for {}", tileEntity.javaClass.name, it) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
  }

  private class GregTechAccessor(private val metaTileEntity: Method) {
    private val localNames = HashMap<Class<*>, Method?>()

    fun localName(tileEntity: TileEntity): String? {
      val meta = metaTileEntity.invoke(tileEntity) ?: return null
      val method =
          localNames.getOrPut(meta.javaClass) {
            runCatching { meta.javaClass.getMethod("getLocalName") }.getOrNull()
                ?: runCatching { meta.javaClass.getMethod("getMetaName") }.getOrNull()
          } ?: return null
      return method.invoke(meta) as? String
    }

    companion object {
      fun of(type: Class<*>): GregTechAccessor? =
          runCatching { type.getMethod("getMetaTileEntity") }.getOrNull()?.let(::GregTechAccessor)
    }
  }
}
