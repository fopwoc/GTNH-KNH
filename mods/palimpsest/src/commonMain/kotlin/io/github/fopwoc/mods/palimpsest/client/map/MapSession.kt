package io.github.fopwoc.mods.palimpsest.client.map

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingMark
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig
import io.github.fopwoc.mods.palimpsest.map.WorldMap
import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.mods.palimpsest.tree.MachineId
import io.github.fopwoc.mods.palimpsest.waypoint.WaypointStore
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private val prospecting: () -> List<ProspectingMark> = { emptyList() },
    val prospectingAvailable: Boolean = false,
) : AutoCloseable {
    private val logger = logger<MapSession>()
    val machineId: Int = MachineId.load(directory)
    val blocks: BlockTable = BlockTable(directory, machineId)
    val waypoints = WaypointStore(directory.resolve("waypoints"))
    private val mutableProspectingMarks = MutableStateFlow<List<ProspectingMark>>(emptyList())
    val prospectingMarks = mutableProspectingMarks.asStateFlow()

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
    private var prospectingFailed = false

    init {
        // The machine id is local by definition; everything else in the directory is map data.
        val ignore = directory.resolve(".gitignore")
        if (!Files.exists(ignore)) Files.writeString(ignore, "${MachineId.FILE_NAME}\n*.tmp\n")
        logger.info("Map session at {}: {} known blocks", directory, blocks.size)
    }

    /**
     * Every client tick: scan a few nearby chunks; once a second persist the vocabulary, then
     * commit. The vocabulary goes first so every block id a commit writes is already saved.
     */
    fun tick() {
        scanner.tick()
        if (++ticks % TICKS_PER_SECOND != 0) return
        runCatching(prospecting)
            .onSuccess {
                mutableProspectingMarks.value = it
                prospectingFailed = false
            }
            .onFailure {
                mutableProspectingMarks.value = emptyList()
                if (!prospectingFailed) logger.warn("Could not read prospecting data", it)
                prospectingFailed = true
            }
        blocks.saveIfDirty()
        map.tick()
    }

    override fun close() {
        scanner.flush()
        blocks.saveIfDirty()
        map.close()
    }

    private companion object {
        const val TICKS_PER_SECOND = 20
    }
}
