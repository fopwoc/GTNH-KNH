package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.codec.BiomeCodec
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.SectionCodec
import io.github.fopwoc.palimpsest.db.codec.SectionDelta
import io.github.fopwoc.palimpsest.db.utils.LruCache

/**
 * Reads and decodes stored blobs by position, keeping the [capacity] most recently used (4096
 * sections ≈ 64 MiB). Decoded arrays are shared and must not be modified.
 */
internal class BlobReader(private val segments: () -> List<SegmentFile>, capacity: Int = 4096) {
    private val cache = LruCache<Long, IntArray>(capacity)

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
        cache.get(position)?.let {
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
        cache.put(position, decoded)
        return decoded
    }

    private companion object {
        val NO_BASES: (Long) -> LongArray? = { null }
    }
}
