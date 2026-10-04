package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.codec.BiomeCodec
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.SectionCodec
import io.github.fopwoc.palimpsest.db.codec.SectionDelta

/**
 * Reads and decodes stored blobs by position, keeping the [capacity] most recently used (4096
 * sections ≈ 64 MiB). Decoded arrays are shared and must not be modified.
 */
internal class BlobReader(
    private val segments: () -> List<SegmentFile>,
    private val capacity: Int = 4096,
) {
    private val cache =
        object : LinkedHashMap<Long, IntArray>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, IntArray>) =
                size > capacity
        }

    /**
     * The blob's content; [baseOf] gives a delta's base as (position, length), and a delta is
     * decoded on top of its base, recursively down to a full section.
     */
    fun decode(
        position: Long,
        length: Int,
        kind: BlobKind,
        baseOf: (Long) -> LongArray? = NO_BASES,
    ): IntArray {
        synchronized(cache) { cache[position] }
            ?.let {
                return it
            }
        val bytes = segments()[Positions.segment(position)].read(Positions.offset(position), length)
        val decoded =
            when (kind) {
                BlobKind.SECTION ->
                    baseOf(position)?.let { (base, baseLength) ->
                        SectionDelta.apply(
                            decode(base, baseLength.toInt(), BlobKind.SECTION, baseOf),
                            bytes,
                        )
                    } ?: SectionCodec.decode(ByteSource(bytes))
                BlobKind.BIOMES -> BiomeCodec.decode(ByteSource(bytes))
            }
        synchronized(cache) { cache[position] = decoded }
        return decoded
    }

    private companion object {
        val NO_BASES: (Long) -> LongArray? = { null }
    }
}
