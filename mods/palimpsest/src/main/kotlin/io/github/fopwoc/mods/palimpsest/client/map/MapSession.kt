package io.github.fopwoc.mods.palimpsest.client.map

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.world.BlockColorTable
import io.github.fopwoc.mods.framework.world.WorldPalette
import io.github.fopwoc.mods.framework.world.minecraft.BiomeTints
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import io.github.fopwoc.mods.palimpsest.map.MapChannel
import io.github.fopwoc.mods.palimpsest.map.PixelShader
import io.github.fopwoc.mods.palimpsest.map.WorldMap
import java.nio.file.Path
import org.apache.logging.log4j.LogManager

/**
 * One open map: the vocabulary files, the history, and the scanner, for one world and dimension.
 * The directory is what you put under git: `<instance>/palimpsest/maps/<world>/dim<N>/`. The
 * vocabulary (block table, palette) is shared by the dimension; the history lives in a slice
 * directory named by the ceiling the scan looks down from, `y255/` for the surface, so cave slices
 * at other ceilings can sit next to it as further maps.
 */
@SideOnly(Side.CLIENT)
class MapSession(val directory: Path, val dimension: Int) : AutoCloseable {
    private val logger = LogManager.getLogger(MapSession::class.java)
    val table: BlockColorTable = BlockColorTable.load(directory.resolve(BlockColorTable.FILE_NAME))
    val palette: WorldPalette =
        WorldPalette.loadOrCreate(directory.resolve(PALETTE_FILE)) {
            val colors = BlockColors.distinctColors()
            logger.info(
                "Deriving map palette from {} plain and {} tintable block colors",
                colors.plain.size,
                colors.tintable.size,
            )
            WorldPalette.derive(colors.plain, colors.tintable)
        }
    private val tints: IntArray = BiomeTints.table()
    /** The ceiling the surface map scans from: the top of a 16-section chunk. */
    val ceiling: Int = SURFACE_CEILING
    val map =
        WorldMap(
            directory.resolve("y$ceiling"),
            listOf(MapChannel.COLORS, MapChannel.BIOMES),
            PixelShader { values ->
                val entry = values[0]
                val color = palette.argb(entry)
                val biome = values[1]
                if (palette.isTintable(entry) && biome in tints.indices)
                    BiomeTints.apply(color, tints[biome])
                else color
            },
        )
    val scanner = ChunkScanner(this)

    private var ticks = 0

    /** Every client tick: scan a few nearby chunks; once a second commit and persist vocabulary. */
    fun tick() {
        scanner.tick()
        if (++ticks % TICKS_PER_SECOND != 0) return
        map.tick()
        table.saveIfDirty(directory.resolve(BlockColorTable.FILE_NAME))
    }

    override fun close() {
        scanner.flush()
        map.close()
        table.saveIfDirty(directory.resolve(BlockColorTable.FILE_NAME))
    }

    companion object {
        const val PALETTE_FILE = "palette.bin"
        const val SURFACE_CEILING = 255
        private const val TICKS_PER_SECOND = 20
    }
}
