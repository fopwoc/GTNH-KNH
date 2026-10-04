package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.Biomes
import io.github.fopwoc.palimpsest.db.BlockKind
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.codec.BiomeCodec
import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ContentHash
import io.github.fopwoc.palimpsest.db.codec.SectionCodec
import io.github.fopwoc.palimpsest.db.codec.SectionDelta
import io.github.fopwoc.palimpsest.db.index.DimensionIndex
import io.github.fopwoc.palimpsest.db.index.RegionIndex
import io.github.fopwoc.palimpsest.db.index.Versions
import io.github.fopwoc.palimpsest.db.store.BlobKind
import io.github.fopwoc.palimpsest.db.store.BlobReader
import io.github.fopwoc.palimpsest.db.store.BlobRef
import io.github.fopwoc.palimpsest.db.store.ChunkPatch
import io.github.fopwoc.palimpsest.db.surface.Sections
import io.github.fopwoc.palimpsest.db.surface.SurfaceScan
import io.github.fopwoc.palimpsest.db.utils.LruCache
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns an observation into a patch against the chunk's previous version: what changed, encoded as
 * deltas, deduplicated or fresh blobs, plus its surface. Thread-safe across chunks; a chunk's
 * commits must be prepared one after another, which the commit chain guarantees. A patch stays
 * [inFlight] until [written], so the next commit compares against it even before it is indexed.
 */
internal class ChunkPreparer(
    private val index: DimensionIndex,
    private val reader: BlobReader,
    private val kind: (Int) -> BlockKind,
) {
    private val dedup = DedupCache()
    private val inFlight = ConcurrentHashMap<ChunkPos, ChunkPatch>()

    /** Per recently observed chunk: its lowest section, then each section's packed fingerprint. */
    private val observed = LruCache<ChunkPos, LongArray>(OBSERVED)

    /**
     * Neighbours share content (solid stone, open sky), so a region's chunks seed deduplication.
     */
    fun seed(region: RegionIndex) {
        region.latest().forEach { version ->
            Versions.refs(version).forEach { blob ->
                blob?.takeUnless { it.delta }?.let(dedup::remember)
            }
        }
    }

    /** [patch] is in the index now. */
    fun written(patch: ChunkPatch) {
        inFlight.remove(patch.pos, patch)
    }

    /** The previous version's slots, if its shape matches [slotCount] slots from [minSection]. */
    private fun previous(pos: ChunkPos, minSection: Int, slotCount: Int): Array<BlobRef?>? =
        (inFlight[pos]?.let { it.minSection to it.slots }
                ?: index.latest(pos)?.let { Versions.minSection(it) to Versions.refs(it) })
            ?.takeIf { (min, slots) -> min == minSection && slots.size == slotCount }
            ?.second

    /**
     * The chunk's patch against its previous version, or null when nothing changed. A section whose
     * packed form matches the chunk's last observation keeps its previous slot without being
     * unpacked or hashed: most of a commit is chunks that did not change.
     */
    fun prepare(
        observation: ChunkObservation,
        fresh: MutableCollection<BlobRef>,
    ): ChunkPatch? {
        val sectionCount = observation.sections.size
        val slotCount = sectionCount + 1
        val previous = previous(observation.pos, observation.minSection, slotCount)
        val seen =
            observed.get(observation.pos)?.takeIf {
                it.size == slotCount && it[0] == observation.minSection.toLong()
            }
        val fingerprints = LongArray(slotCount).also { it[0] = observation.minSection.toLong() }
        val slots = arrayOfNulls<BlobRef>(slotCount)
        val contents = arrayOfNulls<IntArray>(sectionCount)
        var mask = 0L
        fun place(slot: Int, values: IntArray?, kind: BlobKind) {
            val hash = values?.let { ContentHash.of(it, kind.ordinal) }
            val before = previous?.get(slot)
            if (previous != null && hash == before?.hash) {
                slots[slot] = before
                return
            }
            mask = mask or (1L shl slot)
            slots[slot] = values?.let {
                val delta =
                    if (kind == BlobKind.SECTION) delta(observation.pos, before, hash!!, it)
                    else null
                delta?.also(fresh::add)
                    ?: obtain(hash!!, kind, fresh) {
                        val sink = ByteSink(if (kind == BlobKind.SECTION) 512 else 64)
                        if (kind == BlobKind.SECTION) SectionCodec.encode(it, sink)
                        else BiomeCodec.encode(it, sink)
                        sink.toByteArray()
                    }
            }
        }
        observation.sections.forEachIndexed { slot, section ->
            val fingerprint = section?.fingerprint() ?: 0L
            fingerprints[slot + 1] = fingerprint
            if (previous != null && seen != null && seen[slot + 1] == fingerprint) {
                slots[slot] = previous[slot]
                return@forEachIndexed
            }
            val blocks = section?.unpack()?.takeUnless { blocks -> blocks.all { it == 0 } }
            contents[slot] = blocks
            place(slot, blocks, BlobKind.SECTION)
        }
        val biomes =
            when (val biomes = observation.biomes) {
                is Biomes.Columns -> biomes.values
            }
        place(sectionCount, biomes, BlobKind.BIOMES)
        observed.put(observation.pos, fingerprints)
        if (mask == 0L) return null
        // Sections skipped above are unpacked only if the scan reaches them.
        val sections =
            Sections(sectionCount, { slots[it] != null }) {
                contents[it] ?: observation.sections[it]!!.unpack()
            }
        val surface = SurfaceScan.scan(sections, observation.minSection, biomes, kind)
        val encoded = surface.encode(observation.minSection * 16)
        return ChunkPatch(
                observation.pos,
                observation.minSection,
                mask,
                slots,
                encoded,
                surface.sample(),
            )
            .also { inFlight[observation.pos] = it }
    }

    /**
     * The section as a delta against [before], the same slot's previous version, when that is worth
     * it: [before] is stored, its chain is short, and no full blob with this content is remembered.
     * Deltas never enter deduplication; their base is this chunk's own history.
     */
    private fun delta(
        pos: ChunkPos,
        before: BlobRef?,
        hash: ContentHash,
        after: IntArray,
    ): BlobRef? {
        if (before == null || !before.positioned || dedup.get(hash) != null) return null
        val bases = index.bases(pos)
        val depth = if (before.delta) index.depth(pos, before.position) else 0
        if (depth >= MAX_CHAIN) return null
        val previous = reader.decode(before.position, before.length, BlobKind.SECTION, bases)
        val bytes = SectionDelta.encode(previous, after) ?: return null
        return BlobRef.delta(hash, bytes, before, depth)
    }

    /** A stored blob for [hash] if one is remembered, otherwise a fresh one from [encode]. */
    private fun obtain(
        hash: ContentHash,
        kind: BlobKind,
        fresh: MutableCollection<BlobRef>,
        encode: () -> ByteArray,
    ): BlobRef {
        dedup.get(hash)?.let {
            return it
        }
        val blob = BlobRef.fresh(hash, kind, encode())
        return dedup.remember(blob).also { if (it === blob) fresh += blob }
    }

    private companion object {
        /**
         * Deltas in a row before a section is stored whole again: a read decodes at most this many.
         */
        const val MAX_CHAIN = 8

        /** Chunks whose last observation is remembered: far more than are ever loaded at once. */
        const val OBSERVED = 16_384
    }
}
