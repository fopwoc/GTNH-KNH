/*? if >=26 {*/
// Not ported to 1.21.1 yet: the whole file exists only from 26.x.
package io.github.fopwoc.mods.framework.world.minecraft

import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome

/**
 * Biome ids and tints for maps. Registry ids differ between packs and servers, so a biome's id is a
 * 16-bit hash of its key (`minecraft:plains`), stable wherever the biome exists; a rare collision
 * only makes two biomes share a tint. Tables are indexed by that id.
 */
object BiomeTints {
    const val WHITE = 0xFFFFFF
    private const val IDS = 1 shl 16
    /** Vanilla's default water colour; tints are relative to it, so plain water stays white. */
    private const val DEFAULT_WATER = 0x3F76E4

    fun id(biome: Holder<Biome>): Int =
        biome.unwrapKey().map { id(it.identifier().toString()) }.orElse(0)

    fun id(key: String): Int {
        // FNV-1a, folded to 16 bits.
        var hash = 0x811C9DC5.toInt()
        for (char in key) hash = (hash xor char.code) * 0x01000193
        return (hash xor (hash ushr 16)) and 0xFFFF
    }

    /** Grass colour per biome id: what grass, ferns and vines are multiplied by. */
    fun table(level: Level): IntArray = table(level) { it.getGrassColor(0.0, 0.0) }

    /** Foliage colour per biome id: what leaves are multiplied by. */
    fun foliageTable(level: Level): IntArray = table(level) { it.foliageColor }

    /**
     * Water colour per biome id relative to vanilla's default: white almost everywhere, murky in
     * swamps.
     */
    fun waterTable(level: Level): IntArray =
        table(level) { biome ->
            val water = biome.waterColor
            fun channel(shift: Int) =
                ((water shr shift and 255) * 255 / (DEFAULT_WATER shr shift and 255)).coerceAtMost(
                    255
                )
            (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }

    private inline fun table(level: Level, crossinline color: (Biome) -> Int): IntArray {
        val table = IntArray(IDS) { WHITE }
        level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().forEach { biome ->
            runCatching { color(biome.value()) and WHITE }.onSuccess { table[id(biome)] = it }
        }
        return table
    }
}
/*?}*/
