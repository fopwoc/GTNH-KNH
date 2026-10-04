package io.github.fopwoc.palimpsest.db.index

import io.github.fopwoc.palimpsest.db.codec.ContentHash
import io.github.fopwoc.palimpsest.db.store.BlobKind
import io.github.fopwoc.palimpsest.db.store.BlobRef
import io.github.fopwoc.palimpsest.db.store.Positions

/**
 * A chunk version packed into one [LongArray], so a loaded region costs a few arrays per chunk
 * instead of an object per section: the tick, the lowest section, then six longs per slot (blob
 * position, length, content hash high and low, and for a delta its base's position and length). An
 * air slot has position [Positions.AIR], a full blob base [Positions.AIR].
 */
internal object Versions {
    private const val HEADER = 2
    private const val STRIDE = 6

    fun of(tick: Long, minSection: Int, slots: Array<BlobRef?>): LongArray {
        val version = empty(tick, minSection, slots.size)
        slots.forEachIndexed { slot, blob ->
            if (blob != null)
                set(
                    version,
                    slot,
                    blob.position,
                    blob.length,
                    blob.hash,
                    blob.basePosition,
                    blob.baseLength,
                )
        }
        return version
    }

    fun empty(tick: Long, minSection: Int, slots: Int): LongArray =
        LongArray(HEADER + slots * STRIDE).also {
            it[0] = tick
            it[1] = minSection.toLong()
            for (slot in 0 until slots) {
                it[HEADER + slot * STRIDE] = Positions.AIR
                it[HEADER + slot * STRIDE + 4] = Positions.AIR
            }
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

    fun basePosition(version: LongArray, slot: Int): Long = version[HEADER + slot * STRIDE + 4]

    fun baseLength(version: LongArray, slot: Int): Int = version[HEADER + slot * STRIDE + 5].toInt()

    fun set(
        version: LongArray,
        slot: Int,
        position: Long,
        length: Int,
        hash: ContentHash?,
        basePosition: Long = Positions.AIR,
        baseLength: Int = 0,
    ) {
        val at = HEADER + slot * STRIDE
        version[at] = position
        version[at + 1] = length.toLong()
        version[at + 2] = hash?.high ?: 0
        version[at + 3] = hash?.low ?: 0
        version[at + 4] = basePosition
        version[at + 5] = baseLength.toLong()
    }

    /** Sets only the content hash of a slot, leaving its position and base as they are. */
    fun setHash(version: LongArray, slot: Int, hash: ContentHash) {
        val at = HEADER + slot * STRIDE
        version[at + 2] = hash.high
        version[at + 3] = hash.low
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
                    basePosition(version, slot),
                    baseLength(version, slot),
                )
            }
        }
    }
}
