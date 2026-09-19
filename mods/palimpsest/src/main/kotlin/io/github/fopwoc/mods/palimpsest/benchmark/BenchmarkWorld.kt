package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.map.MapPageCache
import io.github.fopwoc.mods.palimpsest.render.PageBuilder
import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.SegmentSet
import java.awt.Color
import java.nio.file.Path

/**
 * A synthetic map with no block vocabulary: block ids are colors straight from a fixed palette,
 * heights stay flat, so every benchmark pixel is the id it was written as. One tree, one page
 * builder, one page cache, the same way the real map wires them.
 */
internal class BenchmarkWorld(directory: Path, sealBytes: Int = SegmentSet.DEFAULT_SEAL_BYTES) :
    AutoCloseable {
    val tree = MapTree(directory, MACHINE, sealBytes)
    val shader = TerrainShader({ id -> palette[id and 255] }, { false }, { WHITE })
    val builder = PageBuilder(tree, shader)
    val pages = MapPageCache(builder, tree)

    val latestEpoch: Long
        get() = tree.latestEpoch

    override fun close() = tree.close()

    companion object {
        const val MACHINE = 0x5041_4c49
        private const val WHITE = 0xFFFFFF

        /** 32 hues × 8 shades, so neighbouring ids differ visibly. */
        val palette =
            IntArray(256) { index ->
                val hue = (index ushr 3) / 32f
                val shade = index and 7
                Color.HSBtoRGB(hue, 0.62f, 0.36f + shade * 0.085f) and 0xFFFFFF
            }

        /** The color a page shows for a flat block id: its palette entry at the flat shade. */
        fun shown(id: Int): Int =
            TerrainShader.shade(palette[id and 255] or (0xFF shl 24), TerrainShader.SHADES[1])
    }
}
