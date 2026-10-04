package io.github.fopwoc.mods.palimpsest.prototype.volume.storage.tree

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.ChunkVolume
import io.github.fopwoc.mods.palimpsest.prototype.volume.storage.SectionCodec
import io.github.fopwoc.mods.palimpsest.tree.ByteSink
import io.github.fopwoc.mods.palimpsest.tree.ByteSource

/**
 * Section versions in the pack, the way generation 2 stores tile versions: a full record, or a
 * delta naming its previous version and only the blocks that differ. Chains stop at [MAX_DEPTH]
 * deltas with a full checkpoint. Identical full sections are stored once anywhere.
 */
class SectionVersions(private val pack: Pack, private val cache: RefCache<IntArray>) {
    private val fullByHash = HashMap<Long, Long>()
    private val depthOf = HashMap<Long, Int>()
    private val hashOf = HashMap<Long, Long>()

    var fullBytes = 0L
        private set
    var deltaBytes = 0L
        private set

    /** Content hash of the section a ref names; the writer's cheap "unchanged?" test. */
    fun hash(ref: Long): Long = hashOf.getValue(ref)

    /** Stores [blocks] as the next version after [previous] (0 when there is none). */
    fun write(blocks: IntArray, hash: Long, previous: Long): Long {
        fullByHash[hash]?.let { return it }
        if (previous != 0L && depthOf.getValue(previous) < MAX_DEPTH) {
            val delta = encodeDelta(read(previous), blocks, previous)
            if (delta != null) {
                val ref = pack.append(delta)
                deltaBytes += delta.size
                depthOf[ref] = depthOf.getValue(previous) + 1
                hashOf[ref] = hash
                cache.put(ref, blocks)
                return ref
            }
        }
        val record = ByteSink(512).apply {
            byte(FULL)
            bytes(SectionCodec.encode(blocks))
        }.toByteArray()
        val ref = pack.append(record)
        fullBytes += record.size
        fullByHash[hash] = ref
        depthOf[ref] = 0
        hashOf[ref] = hash
        cache.put(ref, blocks)
        return ref
    }

    fun read(ref: Long): IntArray =
        cache.get(ref) {
            val bytes = pack.read(ref)
            val source = ByteSource(bytes, 1)
            when (bytes[0].toInt()) {
                FULL -> SectionCodec.decode(bytes.copyOfRange(1, bytes.size))
                else -> {
                    val base = read(Pack.readRef(source, Pack.offset(ref))).copyOf()
                    var position = -1
                    repeat(source.varintInt()) {
                        position += source.varintInt() + 1
                        base[position] = source.varintInt()
                    }
                    base
                }
            }
        }

    /**
     * Changed positions as gaps and new ids as varints, or null when a full record is likely
     * smaller: more than [DELTA_LIMIT] changed blocks.
     */
    private fun encodeDelta(before: IntArray, after: IntArray, previous: Long): ByteArray? {
        var changed = 0
        for (at in 0 until ChunkVolume.SECTION_BLOCKS) if (before[at] != after[at]) changed++
        if (changed > DELTA_LIMIT) return null
        val sink = ByteSink(16 + changed * 4)
        sink.byte(DELTA)
        Pack.writeRef(sink, previous, pack.next)
        sink.varint(changed)
        var last = -1
        for (at in 0 until ChunkVolume.SECTION_BLOCKS) {
            if (before[at] == after[at]) continue
            sink.varint(at - last - 1)
            sink.varint(after[at])
            last = at
        }
        return sink.toByteArray()
    }

    private companion object {
        const val FULL = 0
        const val DELTA = 1
        const val MAX_DEPTH = 16
        const val DELTA_LIMIT = 96
    }
}
