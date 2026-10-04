package io.github.fopwoc.palimpsest.db.index

import io.github.fopwoc.palimpsest.db.BlockKind
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.Commit
import io.github.fopwoc.palimpsest.db.WorldTick
import io.github.fopwoc.palimpsest.db.codec.ContentHash
import io.github.fopwoc.palimpsest.db.store.BlobKind
import io.github.fopwoc.palimpsest.db.store.BlobReader
import io.github.fopwoc.palimpsest.db.store.CommitFrame
import io.github.fopwoc.palimpsest.db.store.Positions
import io.github.fopwoc.palimpsest.db.store.SegmentFile
import io.github.fopwoc.palimpsest.db.surface.Sections
import io.github.fopwoc.palimpsest.db.surface.SurfaceScan

/**
 * Brings [index] up to date with truth: applies every commit frame past its coverage. Hashes and
 * surfaces are not truth, so every chunk version a frame introduces is decoded, once, on the common
 * pool, and both are computed from it: a full rebuild reads every stored section, catching up after
 * a crash only a few frames.
 */
internal class IndexReplay(
    private val index: DimensionIndex,
    private val segments: List<SegmentFile>,
    private val kind: (Int) -> BlockKind,
    private val progress: (bytes: Long) -> Unit = {},
) {
    /** Bytes of history [run] will read. */
    val pending: Long
        get() = segments.sumOf { it.length - start(it) }

    private fun start(segment: SegmentFile): Long =
        index.covered.getOrNull(segment.ordinal)?.length ?: SegmentFile.firstFrame(segment)

    private val reader = BlobReader({ segments }, capacity = 1024)

    /** Returns the number of commit frames applied. */
    fun run(): Int {
        var frames = 0
        for (segment in segments) {
            for (frame in CommitFrame.all(segment, start(segment), withBlobs = false)) {
                apply(frame)
                progress(frame.end - frame.start)
                frames++
            }
            index.cover(segment.ordinal, segment.name, segment.length)
        }
        return frames
    }

    /** Applies one commit frame to the index. */
    private fun apply(frame: CommitFrame) {
        val decoded = frame.record

        // Deltas this frame introduces; older ones are already in their region's index.
        val frameBases = HashMap<Long, LongArray>()
        for (index in decoded.blobs.indices) if (decoded.bases[index] != Positions.AIR)
            frameBases[decoded.blobs[index]] =
                longArrayOf(decoded.bases[index], decoded.baseLengths[index].toLong())

        // Positions first: unchanged slots come from the previous version, already indexed.
        val versions =
            decoded.patches.map { patch ->
                val previous = index.latest(patch.pos)?.takeIf { Versions.slots(it) == patch.slots }
                Versions.empty(decoded.tick, patch.minSection, patch.slots).also { version ->
                    for (slot in 0 until patch.slots) {
                        if (patch.mask and (1L shl slot) == 0L)
                            previous?.let { Versions.copy(it, version, slot) }
                        else if (patch.positions[slot] != Positions.AIR) {
                            val base = frameBases[patch.positions[slot]]
                            Versions.set(
                                version,
                                slot,
                                patch.positions[slot],
                                patch.lengths[slot],
                                null,
                                base?.get(0) ?: Positions.AIR,
                                base?.get(1)?.toInt() ?: 0,
                            )
                        }
                    }
                }
            }
        val contents = decodeAll(decoded.patches.map { it.pos }.zip(versions), frameBases)
        val summaries =
            decoded.patches.indices
                .toList()
                .parallelStream()
                .map { i -> summarize(versions[i], decoded.patches[i].mask, contents) }
                .toList()
        decoded.patches.forEachIndexed { i, patch ->
            index.append(patch.pos, versions[i], summaries[i].first, summaries[i].second)
        }
        val sections = decoded.blobLengths.size
        index.append(
            Commit(
                WorldTick(decoded.tick),
                decoded.observedAt,
                decoded.patches.size,
                sections,
                frame.end - frame.start,
            ),
            decoded.patches.map { RegionKey.of(it.pos) }.toSet(),
        )
    }

    /**
     * Fills in the hashes of [version]'s changed slots and returns its encoded surface and sample,
     * scanned from the decoded sections.
     */
    private fun summarize(
        version: LongArray,
        mask: Long,
        contents: Map<Long, IntArray>,
    ): Pair<ByteArray, IntArray> {
        val slots = Versions.slots(version)
        for (slot in 0 until slots) {
            val position = Versions.position(version, slot)
            if (position == Positions.AIR || mask and (1L shl slot) == 0L) continue
            val hash = ContentHash.of(contents.getValue(position), BlobKind.of(slot, slots).ordinal)
            Versions.setHash(version, slot, hash)
        }
        val sections =
            Array(slots - 1) { slot ->
                Versions.position(version, slot)
                    .takeIf { it != Positions.AIR }
                    ?.let(contents::getValue)
            }
        val biomes = contents.getValue(Versions.position(version, slots - 1))
        val surface =
            SurfaceScan.scan(Sections.of(sections), Versions.minSection(version), biomes, kind)
        return surface.encode(Versions.minSection(version) * 16) to surface.sample()
    }

    /** Every section and biome blob the versions hold, decoded once, by position. */
    private class Wanted(val length: Int, val kind: BlobKind, val pos: ChunkPos)

    /**
     * Every section and biome blob the versions hold, decoded once, by position; a delta on top of
     * its base, found among [frameBases] or in its chunk's region.
     */
    private fun decodeAll(
        versions: List<Pair<ChunkPos, LongArray>>,
        frameBases: Map<Long, LongArray>,
    ): Map<Long, IntArray> {
        val wanted = HashMap<Long, Wanted>()
        for ((pos, version) in versions) {
            val slots = Versions.slots(version)
            for (slot in 0 until slots) {
                val position = Versions.position(version, slot)
                if (position != Positions.AIR)
                    wanted[position] =
                        Wanted(Versions.length(version, slot), BlobKind.of(slot, slots), pos)
            }
        }
        return wanted.entries
            .toList()
            .parallelStream()
            .map { (position, blob) ->
                val regionBases = index.bases(blob.pos)
                position to
                    reader.decode(position, blob.length, blob.kind) {
                        frameBases[it] ?: regionBases(it)
                    }
            }
            .toList()
            .toMap()
    }
}
