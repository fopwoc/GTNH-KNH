package io.github.fopwoc.mods.palimpsest.client.map

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig
import io.github.fopwoc.mods.palimpsest.map.WorldMap
import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.mods.palimpsest.tree.MachineId
import java.nio.file.Files
import java.nio.file.Path

/**
 * One open map: the block vocabulary, the history, and the scanner, for one world and dimension.
 * The directory is what you put under git: `<instance>/palimpsest/maps/<world>/<dimension>/`. The
 * vocabulary (`blocks.<machine>.tsv`) is shared by the dimension; the history lives in a slice
 * directory named by the [ceiling] the scan looks down from, `y255/` for the GTNH surface, so cave
 * slices at other ceilings can sit next to it as further maps.
 */
class MapSession(
    val directory: Path,
    val ceiling: Int,
    tints: BiomeTints,
    scanner: (MapSession) -> MapScanner,
) : AutoCloseable {
    private val logger = logger<MapSession>()
    val machineId: Int = MachineId.load(directory)
    val blocks: BlockTable = BlockTable(directory, machineId)

    val map =
        WorldMap(
            directory.resolve("y$ceiling"),
            blocks,
            tints.grass,
            tints.foliage,
            tints.water,
            commitInterval = PalimpsestConfig::commitInterval,
        )
    val scanner = scanner(this)

    private var ticks = 0

    init {
        // The machine id is local by definition; everything else in the directory is map data.
        val ignore = directory.resolve(".gitignore")
        if (!Files.exists(ignore)) Files.writeString(ignore, "${MachineId.FILE_NAME}\n*.tmp\n")
        logger.info("Map session at {}: {} known blocks", directory, blocks.size)
    }

    /** Every client tick: scan a few nearby chunks; once a second commit and persist vocabulary. */
    fun tick() {
        scanner.tick()
        if (++ticks % TICKS_PER_SECOND != 0) return
        map.tick()
        blocks.saveIfDirty()
    }

    override fun close() {
        scanner.flush()
        map.close()
        blocks.saveIfDirty()
    }

    private companion object {
        const val TICKS_PER_SECOND = 20
    }
}
