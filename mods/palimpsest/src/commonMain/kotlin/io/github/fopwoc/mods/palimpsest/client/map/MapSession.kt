package io.github.fopwoc.mods.palimpsest.client.map

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.palimpsest.client.claim.ClaimMark
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingMark
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig
import io.github.fopwoc.mods.palimpsest.history.DimensionHistory
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import io.github.fopwoc.mods.palimpsest.map.MapView
import io.github.fopwoc.mods.palimpsest.map.MinimapBroker
import io.github.fopwoc.mods.palimpsest.map.PageSampleBudget
import io.github.fopwoc.mods.palimpsest.map.WorldMap
import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.mods.palimpsest.tree.MachineId
import io.github.fopwoc.mods.palimpsest.waypoint.WaypointStore
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One open map for one world and dimension: the colours of its blocks and its waypoints in
 * `<instance>/palimpsest/maps/<world>/<dimension>/`, its [history] in the world's database, and the
 * scanner. [ceiling] is the Y the live scan looks down from.
 */
class MapSession(
    val directory: Path,
    val ceiling: Int,
    tints: BiomeTints,
    scanner: (MapSession) -> MapScanner,
    history: (BlockTable) -> DimensionHistory?,
    worldTime: () -> Long,
    private val prospecting: () -> List<ProspectingMark> = { emptyList() },
    val prospectingAvailable: Boolean = false,
    val nodeTrackingAvailable: Boolean = false,
    private val claims: () -> List<ClaimMark> = { emptyList() },
    private val claimRequester: (MapCamera) -> Unit = {},
    val claimsAvailable: Boolean = false,
) : AutoCloseable {
    private val logger = logger<MapSession>()

    val machineId: Int = MachineId.load(directory)
    val blocks: BlockTable = BlockTable(directory, machineId)
    val waypoints = WaypointStore(directory.resolve("waypoints"))
    private val mutableProspectingMarks = MutableStateFlow<List<ProspectingMark>>(emptyList())
    val prospectingMarks = mutableProspectingMarks.asStateFlow()
    private val mutableClaimMarks = MutableStateFlow<List<ClaimMark>>(emptyList())
    val claimMarks = mutableClaimMarks.asStateFlow()

    private val sampleBudget = PageSampleBudget()

    val history: DimensionHistory? = history(blocks)

    val map =
        WorldMap(
            blocks,
            this.history,
            worldTime,
            tints.grass,
            tints.foliage,
            tints.water,
            commitInterval = PalimpsestConfig::commitInterval,
            sampleBudget = sampleBudget,
        )
    val minimap = MinimapBroker(blocks, tints.grass, tints.foliage, tints.water, sampleBudget)
    val minimapView = MapView(minimap, parallelism = 2, maxReadyPages = 256)
    val scanner = scanner(this)

    private var ticks = 0
    private var prospectingFailed = false
    private var claimsFailed = false

    init {
        // The machine id is local by definition; everything else in the directory is map data.
        val ignore = directory.resolve(".gitignore")
        if (!Files.exists(ignore)) Files.writeString(ignore, "${MachineId.FILE_NAME}\n*.tmp\n")
        logger.info(
            "Map session at {}: {} known blocks, history {}",
            directory,
            blocks.size,
            if (this.history != null) "open" else "unavailable",
        )
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
        if (claimsAvailable)
            runCatching(claims)
                .onSuccess {
                    mutableClaimMarks.value = it
                    claimsFailed = false
                }
                .onFailure {
                    mutableClaimMarks.value = emptyList()
                    if (!claimsFailed) logger.warn("Could not read ServerUtilities claims", it)
                    claimsFailed = true
                }
        blocks.saveIfDirty()
        map.tick()
    }

    override fun close() {
        scanner.flush()
        minimapView.close()
        blocks.saveIfDirty()
        map.close()
    }

    fun requestClaims(camera: MapCamera) {
        if (claimsAvailable) claimRequester(camera)
    }

    private companion object {
        const val TICKS_PER_SECOND = 20
    }
}
