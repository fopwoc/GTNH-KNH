package io.github.fopwoc.mods.framework.world.minecraft

import io.github.fopwoc.mods.framework.world.ChunkColumns
import io.github.fopwoc.mods.framework.world.MapPalette
import net.minecraft.block.material.MapColor

/** The 256 ARGB colors of vanilla map items, built once from [MapColor.mapColorArray]. */
object VanillaMapPalette {
    val colors: IntArray by lazy {
        MapPalette.build(
            IntArray(ChunkColumns.COLOR_INDEXES) { MapColor.mapColorArray[it]?.colorValue ?: 0 }
        )
    }
}
