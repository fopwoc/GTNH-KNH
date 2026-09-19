package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.file.Files
import java.nio.file.Path

/** A vocabulary of three flat colors and a page-store factory over a temp directory. */
internal object TestBlocks {
    const val RED = 0xFF0000
    const val BLUE = 0x0000FF
    const val GREEN = 0x00FF00

    fun table(directory: Path): BlockTable =
        BlockTable(directory, machineId = 0x7e57).also {
            it.idOf("red", RED, 0)
            it.idOf("blue", BLUE, 0)
            it.idOf("green", GREEN, 0)
        }

    /** What a flat tile of the block shows on a page: its color at the flat shade. */
    fun shown(color: Int): Int =
        io.github.fopwoc.mods.palimpsest.render.TerrainShader.shade(
            color or (0xFF shl 24),
            io.github.fopwoc.mods.palimpsest.render.TerrainShader.SHADES[1],
        )

    fun flat(block: Int): TileRecord = TileRecord.solid(0, block, height = 64, biome = 1)

    inline fun withDirectory(prefix: String, test: (Path) -> Unit) {
        val directory = Files.createTempDirectory(prefix)
        try {
            test(directory)
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }
}
