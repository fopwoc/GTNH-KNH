package io.github.fopwoc.palimpsest.db.index

import io.github.fopwoc.palimpsest.db.Commit
import io.github.fopwoc.palimpsest.db.Retention
import io.github.fopwoc.palimpsest.db.WorldTick
import io.github.fopwoc.palimpsest.db.store.Compactor
import io.github.fopwoc.palimpsest.db.store.Positions
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Moves a complete index along with a compaction instead of rebuilding it: every slot's blob and
 * delta base go to their new positions, and nothing is decoded. A full history keeps every version,
 * overview and timeline as they are; a latest-only one keeps each chunk's last version (its deltas
 * now whole sections), its last sample, and the compacted commits as its timeline.
 */
internal object IndexRemap {
    /** Region files [apply] goes through, for progress. */
    fun regions(directory: Path): Int = regionFiles(directory).size

    fun apply(
        directory: Path,
        retention: Retention,
        compaction: Compactor.Result,
        progress: (Long) -> Unit,
    ) {
        for (file in regionFiles(directory)) {
            val (x, z) = file.name.split('.').let { it[1].toInt() to it[2].toInt() }
            val key = RegionKey(x, z)
            val region = RegionIndex.load(key, file.parent)
            val entries = ArrayList<Triple<Int, LongArray, ByteArray>>()
            for ((local, versions, surfaces) in region.chunks()) {
                when (retention) {
                    Retention.HISTORY ->
                        versions.indices.mapTo(entries) {
                            Triple(
                                local,
                                moved(versions[it], compaction, keepBases = true),
                                surfaces[it],
                            )
                        }
                    Retention.LATEST ->
                        entries +=
                            Triple(
                                local,
                                moved(versions.last(), compaction, keepBases = false),
                                surfaces.last(),
                            )
                }
            }
            region.replace(entries)
            if (retention == Retention.LATEST) {
                val overview = OverviewRegion.load(key, directory.resolve(OverviewIndex.NAME))
                overview.replace(
                    entries.mapNotNull { (local, version, _) ->
                        overview.latest(local)?.let { Triple(local, Versions.tick(version), it) }
                    }
                )
            }
            progress(1)
        }
        if (retention == Retention.LATEST)
            TimelineFile.replace(
                directory.resolve(TIMELINE),
                compaction.frames.map { frame ->
                    Commit(
                        WorldTick(frame.tick),
                        frame.observedAt,
                        frame.chunks.size,
                        frame.blobs,
                        frame.bytes,
                    ) to frame.chunks.map(RegionKey::of).toSet()
                },
            )
        val files =
            (regionFiles(directory) +
                    overviewFiles(directory) +
                    listOf(directory.resolve(TIMELINE)).filter(Files::exists))
                .associate { directory.relativize(it).toString() to Files.size(it) }
        Coverage(
                listOf(Coverage.Segment(compaction.segment.name, compaction.segment.length)),
                files,
            )
            .write(directory)
    }

    /** [version] with its blobs, and its delta bases or none, at their compacted places. */
    private fun moved(
        version: LongArray,
        compaction: Compactor.Result,
        keepBases: Boolean,
    ): LongArray {
        val copy = version.copyOf()
        for (slot in 0 until Versions.slots(version)) {
            val position = Versions.position(version, slot)
            if (position == Positions.AIR) continue
            val base = Versions.basePosition(version, slot)
            val keptBase = keepBases && base != Positions.AIR
            Versions.set(
                copy,
                slot,
                checkNotNull(compaction.moved.get(position)) { "Blob at $position did not move" },
                compaction.lengths[position] ?: Versions.length(version, slot),
                Versions.hash(version, slot),
                if (keptBase)
                    checkNotNull(compaction.moved.get(base)) { "Base at $base did not move" }
                else Positions.AIR,
                if (keptBase) Versions.baseLength(version, slot) else 0,
            )
        }
        return copy
    }

    private fun regionFiles(directory: Path): List<Path> =
        directory
            .resolve("regions")
            .takeIf(Files::isDirectory)
            ?.listDirectoryEntries("r.*.idx")
            .orEmpty()

    private fun overviewFiles(directory: Path): List<Path> =
        directory
            .resolve(OverviewIndex.NAME)
            .takeIf(Files::isDirectory)
            ?.listDirectoryEntries("r.*.ovw")
            .orEmpty()

    private const val TIMELINE = "timeline"
}
