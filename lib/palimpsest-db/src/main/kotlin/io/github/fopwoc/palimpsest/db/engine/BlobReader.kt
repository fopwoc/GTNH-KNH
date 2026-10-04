package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.codec.BiomeCodec
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.SectionCodec
import io.github.fopwoc.palimpsest.db.store.BlobKind
import io.github.fopwoc.palimpsest.db.store.BlobRef
import io.github.fopwoc.palimpsest.db.store.SegmentFile

/**
 * Reads and decodes published blobs, keeping the most recently used ones (4096 sections ≈ 64 MiB).
 * Decoded arrays are shared and must not be modified.
 */
internal class BlobReader(
    private val segments: () -> List<SegmentFile>,
    private val capacity: Int = 4096,
) {
    private val cache =
        object : LinkedHashMap<BlobRef, IntArray>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BlobRef, IntArray>) =
                size > capacity
        }

    fun decode(blob: BlobRef): IntArray {
        synchronized(cache) { cache[blob] }
            ?.let {
                return it
            }
        val bytes = segments()[blob.segment].read(blob.offset, blob.length)
        val decoded =
            when (blob.kind) {
                BlobKind.SECTION -> SectionCodec.decode(ByteSource(bytes))
                BlobKind.BIOMES -> BiomeCodec.decode(ByteSource(bytes))
            }
        synchronized(cache) { cache[blob] = decoded }
        return decoded
    }
}
