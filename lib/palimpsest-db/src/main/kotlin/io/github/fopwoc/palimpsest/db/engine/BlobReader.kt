package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.codec.BiomeCodec
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.SectionCodec
import io.github.fopwoc.palimpsest.db.store.BlobKind
import io.github.fopwoc.palimpsest.db.store.Positions
import io.github.fopwoc.palimpsest.db.store.SegmentFile

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

    fun decode(position: Long, length: Int, kind: BlobKind): IntArray {
        synchronized(cache) { cache[position] }
            ?.let {
                return it
            }
        val bytes = segments()[Positions.segment(position)].read(Positions.offset(position), length)
        val decoded =
            when (kind) {
                BlobKind.SECTION -> SectionCodec.decode(ByteSource(bytes))
                BlobKind.BIOMES -> BiomeCodec.decode(ByteSource(bytes))
            }
        synchronized(cache) { cache[position] = decoded }
        return decoded
    }
}
