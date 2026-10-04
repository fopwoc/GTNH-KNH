package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.ChunkVolume
import io.github.fopwoc.mods.palimpsest.prototype.volume.model.Vocabulary
import io.github.fopwoc.mods.palimpsest.prototype.volume.storage.VolumeStore
import io.github.fopwoc.mods.palimpsest.prototype.volume.storage.tree.VolumeTree
import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * The same observations committed twice: as 2.4 surface tiles through the production [MapTree],
 * and as full volumes through [VolumeStore]. The surface tiles are scanned from those very volumes,
 * so the two histories describe one world.
 */
class PairedStores(root: Path, private val vocabulary: Vocabulary, cacheSections: Int = 16_384) :
    AutoCloseable {
    val surfaceDirectory: Path = root.resolve("surface").createDirectories()
    var tree = MapTree(surfaceDirectory, MACHINE)
        private set
    val volumes = VolumeStore(root.resolve("volume").createDirectories(), cacheSections)
    /** Generation 2's tree carried into 3D: one store with the 2D summary inside its leaves. */
    val tree3d = VolumeTree(root.resolve("tree").createDirectories(), cacheSections)

    val scanTimes = mutableListOf<Long>()
    val surfaceTimes = mutableListOf<Long>()
    val volumeTimes = mutableListOf<Long>()
    val treeTimes = mutableListOf<Long>()
    var tilesWritten = 0L
        private set
    var sectionsWritten = 0L
        private set
    var sectionsReused = 0L
        private set
    var chunkVersions = 0L
        private set

    /** The surface tiles scanned for this commit and the new 3D pack bytes per chunk. */
    class Committed(val tiles: Map<TileKey, TileRecord>, val packed: Map<TileKey, Int>)

    fun commit(epoch: Long, changed: Map<TileKey, ChunkVolume>): Committed {
        val kinds = vocabulary.kinds()
        val tiles = timed(scanTimes) { changed.mapValues { (_, volume) -> surface(volume, kinds, epoch) } }
        val surfaceCommit = timed(surfaceTimes) { tree.commit(epoch, tiles).also { tree.sealIfDue() } }
        val volumeCommit = timed(volumeTimes) { volumes.commit(epoch, changed) }
        timed(treeTimes) { tree3d.commit(epoch, changed, tiles) }
        tilesWritten += surfaceCommit.tilesWritten
        sectionsWritten += volumeCommit.written
        sectionsReused += volumeCommit.reused
        chunkVersions += volumeCommit.chunks
        return Committed(tiles, volumeCommit.packed)
    }

    /** Sealed 2.4 history bytes, the format's at-rest size. */
    fun surfaceBytes(): Long {
        tree.seal()
        return directoryBytes(surfaceDirectory)
    }

    /** A fresh tree over the same files, so reads start with empty decoded caches. */
    fun reopenSurface() {
        tree.close()
        tree = MapTree(surfaceDirectory, MACHINE)
    }

    fun resetTimings() {
        scanTimes.clear()
        surfaceTimes.clear()
        volumeTimes.clear()
        treeTimes.clear()
    }

    override fun close() {
        tree.close()
        volumes.close()
        tree3d.close()
    }

    private companion object {
        const val MACHINE = 1
    }
}
