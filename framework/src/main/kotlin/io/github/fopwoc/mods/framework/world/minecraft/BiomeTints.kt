package io.github.fopwoc.mods.framework.world.minecraft

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import net.minecraft.world.biome.BiomeGenBase

/**
 * The grass color of every biome id, as the world renderer would tint grass at sea level; the
 * multiplier a map applies to tintable palette entries. Sized by the registry, so 256 in vanilla
 * and 65536 under EndlessIDs. Built on the client thread, read anywhere.
 */
@SideOnly(Side.CLIENT)
object BiomeTints {
    private const val WHITE = 0xFFFFFF
    private const val SEA_LEVEL = 64

    /** Grass colour per biome id: what grass, tall grass and vines are multiplied by. */
    fun table(): IntArray = table { it.getBiomeGrassColor(0, SEA_LEVEL, 0) }

    /** Foliage colour per biome id: what leaves are multiplied by; deeper than the grass colour. */
    fun foliageTable(): IntArray = table { it.getBiomeFoliageColor(0, SEA_LEVEL, 0) }

    private inline fun table(color: (BiomeGenBase) -> Int): IntArray {
        val biomes = BiomeGenBase.getBiomeGenArray()
        return IntArray(biomes.size) { id -> biomes.getOrNull(id)?.let { runCatching { color(it) }.getOrNull() } ?: WHITE }
    }

    /** Multiplies an opaque color by a biome tint, keeping alpha. */
    fun apply(argb: Int, tint: Int): Int {
        if (tint == WHITE) return argb
        val r = (argb shr 16 and 255) * (tint shr 16 and 255) / 255
        val g = (argb shr 8 and 255) * (tint shr 8 and 255) / 255
        val b = (argb and 255) * (tint and 255) / 255
        return (argb and (0xFF shl 24)) or (r shl 16) or (g shl 8) or b
    }
}
