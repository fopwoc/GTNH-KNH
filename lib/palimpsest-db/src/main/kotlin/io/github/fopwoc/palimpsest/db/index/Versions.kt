package io.github.fopwoc.palimpsest.db.index

import io.github.fopwoc.palimpsest.db.codec.ContentHash
import io.github.fopwoc.palimpsest.db.store.BlobKind
import io.github.fopwoc.palimpsest.db.store.BlobRef
import io.github.fopwoc.palimpsest.db.store.Positions

/**
 * A chunk version packed into one [LongArray], so a loaded region costs a few arrays per chunk
 * instead of an object per section: the tick, the lowest section, then four longs per slot (blob
 * position, length, content hash high and low). An air slot has position [Positions.AIR].
 */
internal object Versions {
    private const val HEADER = 2
    private const val STRIDE = 4

    fun of(tick: Long, minSection: Int, slots: Array<BlobRef?>): LongArray {
        val version = LongArray(HEADER + slots.size * STRIDE)
        version[0] = tick
        version[1] = minSection.toLong()
        slots.forEachIndexed { slot, blob ->
            set(version, slot, blob?.position ?: Positions.AIR, blob?.length ?: 0, blob?.hash)
        }
        return version
    }

    fun empty(tick: Long, minSection: Int, slots: Int): LongArray =
        LongArray(HEADER + slots * STRIDE).also {
            it[0] = tick
            it[1] = minSection.toLong()
            for (slot in 0 until slots) it[HEADER + slot * STRIDE] = Positions.AIR
        }

    fun tick(version: LongArray): Long = version[0]

    fun minSection(version: LongArray): Int = version[1].toInt()

    fun slots(version: LongArray): Int = (version.size - HEADER) / STRIDE

    fun position(version: LongArray, slot: Int): Long = version[HEADER + slot * STRIDE]

    fun length(version: LongArray, slot: Int): Int = version[HEADER + slot * STRIDE + 1].toInt()

    fun hash(version: LongArray, slot: Int): ContentHash? {
        val at = HEADER + slot * STRIDE
        return if (version[at] == Positions.AIR) null
        else ContentHash(version[at + 2], version[at + 3])
    }

    fun set(version: LongArray, slot: Int, position: Long, length: Int, hash: ContentHash?) {
        val at = HEADER + slot * STRIDE
        version[at] = position
        version[at + 1] = length.toLong()
        version[at + 2] = hash?.high ?: 0
        version[at + 3] = hash?.low ?: 0
    }

    /** Copies slot [slot] of [from] into [to]. */
    fun copy(from: LongArray, to: LongArray, slot: Int) {
        val at = HEADER + slot * STRIDE
        from.copyInto(to, at, at, at + STRIDE)
    }

    fun sameSlot(a: LongArray, b: LongArray, slot: Int): Boolean {
        val at = HEADER + slot * STRIDE
        return a[at] == b[at] && a[at + 2] == b[at + 2] && a[at + 3] == b[at + 3]
    }

    /** The slots as stored blobs, for the commit pipeline to compare against and carry over. */
    fun refs(version: LongArray): Array<BlobRef?> {
        val slots = slots(version)
        return Array(slots) { slot ->
            hash(version, slot)?.let {
                BlobRef.stored(
                    it,
                    BlobKind.of(slot, slots),
                    length(version, slot),
                    position(version, slot),
                )
            }
        }
    }
}
