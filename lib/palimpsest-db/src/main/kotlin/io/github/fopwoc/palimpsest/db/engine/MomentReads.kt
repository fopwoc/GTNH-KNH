package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.Biomes
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.ChunkVolume
import io.github.fopwoc.palimpsest.db.ChunkWindow
import io.github.fopwoc.palimpsest.db.Request
import io.github.fopwoc.palimpsest.db.SampleGrid
import io.github.fopwoc.palimpsest.db.SurfaceGrid
import io.github.fopwoc.palimpsest.db.index.DimensionIndex
import io.github.fopwoc.palimpsest.db.index.OverviewIndex
import io.github.fopwoc.palimpsest.db.index.Versions
import io.github.fopwoc.palimpsest.db.store.BlobKind
import io.github.fopwoc.palimpsest.db.store.BlobReader
import io.github.fopwoc.palimpsest.db.store.Positions
import io.github.fopwoc.palimpsest.db.surface.Sections
import io.github.fopwoc.palimpsest.db.surface.Surface
import io.github.fopwoc.palimpsest.db.surface.SurfaceScan

/**
 * The reads of one loaded dimension at a committed moment, [tick] being the commit's; a null tick
 * is a moment before the first commit and reads as empty. Windows are split into one task per row
 * of chunks so they decode on every read thread at once.
 */
internal class MomentReads(
    private val db: LocalDb,
    private val index: DimensionIndex,
    private val reader: BlobReader,
) {
    /** From the index's surfaces: the map's everyday read. */
    fun surface(window: ChunkWindow, tick: Long?): Request<SurfaceGrid> =
        byRows(window, tick) { pos, at -> index.surfaceAt(pos, at)?.let(Surface::decode) }

    /**
     * The cold path: each chunk's sections decoded from history as its columns reach them, scanned
     * down from [y]. Near a cave ceiling that is usually one or two sections.
     */
    fun ceiling(window: ChunkWindow, y: Int, tick: Long?): Request<SurfaceGrid> =
        byRows(window, tick) { pos, at ->
            index.at(pos, at)?.let { version ->
                val slots = Versions.slots(version)
                val sections =
                    Sections(slots - 1, { Versions.position(version, it) != Positions.AIR }) {
                        decode(pos, version, it)!!
                    }
                SurfaceScan.scan(
                    sections,
                    Versions.minSection(version),
                    decode(pos, version, slots - 1)!!,
                    db.kinds::kind,
                    y,
                )
            }
        }

    fun overview(window: ChunkWindow, level: Int, tick: Long?): Request<SampleGrid> =
        FutureRequest(db.threads.interactive) {
            if (tick == null) OverviewIndex.empty(window, level)
            else index.overview.grid(window, level, tick)
        }

    fun volume(chunk: ChunkPos, tick: Long?): Request<ChunkVolume?> =
        FutureRequest(db.threads.interactive) {
            val version = tick?.let { index.at(chunk, it) } ?: return@FutureRequest null
            val slots = Versions.slots(version)
            DecodedVolume(
                chunk,
                Versions.minSection(version),
                Array(slots - 1) { decode(chunk, version, it) },
                Biomes.Columns(decode(chunk, version, slots - 1)!!),
            )
        }

    /** Slot [slot] of [pos]'s [version] decoded, deltas resolved; null for air. */
    private fun decode(pos: ChunkPos, version: LongArray, slot: Int): IntArray? {
        val position = Versions.position(version, slot)
        if (position == Positions.AIR) return null
        return reader.decode(
            position,
            Versions.length(version, slot),
            BlobKind.of(slot, Versions.slots(version)),
            index.bases(pos),
        )
    }

    private fun byRows(
        window: ChunkWindow,
        tick: Long?,
        read: (ChunkPos, Long) -> Surface?,
    ): Request<SurfaceGrid> {
        val grid = SurfaceGrid.builder(window)
        val rows =
            if (tick == null) emptyList()
            else
                (window.z0 until window.z0 + window.height).map { z ->
                    {
                        for (x in window.x0 until window.x0 + window.width) {
                            val pos = ChunkPos(x, z)
                            val surface = read(pos, tick) ?: continue
                            grid.put(
                                pos,
                                surface.block,
                                surface.height,
                                surface.depth,
                                surface.biome,
                            )
                        }
                    }
                }
        return SplitRequest(db.threads.interactive, rows, grid::build)
    }
}
