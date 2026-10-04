package io.github.fopwoc.palimpsest.db.index

/**
 * Every version of one chunk, in tick order, with its encoded surface. A version is stored whole
 * every [KEYFRAME] versions, or when the chunk's shape changes, and as a patch otherwise: its
 * header, the mask of slots that differ from the version before and only those slots. A chunk that
 * changed thousands of times costs a few dozen bytes per change instead of a full version; reading
 * one decodes at most [KEYFRAME] patches. The latest version is kept whole for the commit pipeline.
 * Not thread-safe: [RegionIndex] guards it.
 */
internal class ChunkVersions {
    private var ticks = LongArray(4)
    private var stored = arrayOfNulls<LongArray>(4)
    private var whole = BooleanArray(4)
    private var surfaces = arrayOfNulls<ByteArray>(4)
    private var lastWhole = -1

    var size = 0
        private set

    var latest: LongArray? = null
        private set

    val latestSurface: ByteArray?
        get() = if (size == 0) null else surfaces[size - 1]

    fun tick(index: Int): Long = ticks[index]

    /**
     * Adds [version], later than every other, with its [surface] (reuse the previous one's array
     * when unchanged).
     */
    fun add(version: LongArray, surface: ByteArray) {
        if (size == ticks.size) grow()
        val previous = latest
        val keyframe =
            previous == null ||
                size - lastWhole >= KEYFRAME ||
                Versions.slots(previous) != Versions.slots(version)
        ticks[size] = Versions.tick(version)
        stored[size] = if (keyframe) version else patch(previous!!, version)
        whole[size] = keyframe
        surfaces[size] = surface
        if (keyframe) lastWhole = size
        latest = version
        size++
    }

    /** Version [index] whole: the nearest stored whole one before it, patched forward. */
    fun version(index: Int): LongArray {
        if (index == size - 1) return latest!!
        var start = index
        while (!whole[start]) start--
        val version = stored[start]!!.copyOf()
        for (at in start + 1..index) apply(version, stored[at]!!)
        return version
    }

    fun surface(index: Int): ByteArray = surfaces[index]!!

    /** Index of the last version at or before [tick], or null. */
    fun search(tick: Long): Int? {
        var low = 0
        var high = size - 1
        var found: Int? = null
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (ticks[middle] <= tick) {
                found = middle
                low = middle + 1
            } else high = middle - 1
        }
        return found
    }

    /** Whether any version has a tick after [from] up to and including [to]. */
    fun changedBetween(from: Long, to: Long): Boolean {
        val after = search(from)?.plus(1) ?: 0
        return after < size && ticks[after] <= to
    }

    private fun grow() {
        val capacity = ticks.size * 2
        ticks = ticks.copyOf(capacity)
        stored = stored.copyOf(capacity)
        whole = whole.copyOf(capacity)
        surfaces = surfaces.copyOf(capacity)
    }

    /** Header, mask, then the changed slots' longs. */
    private fun patch(previous: LongArray, version: LongArray): LongArray {
        val slots = Versions.slots(version)
        var mask = 0L
        var changed = 0
        for (slot in 0 until slots) if (!Versions.identicalSlot(previous, version, slot)) {
            mask = mask or (1L shl slot)
            changed++
        }
        val patch = LongArray(PATCH_HEADER + changed * Versions.STRIDE)
        patch[0] = version[0]
        patch[1] = version[1]
        patch[2] = mask
        var at = PATCH_HEADER
        for (slot in 0 until slots) if (mask and (1L shl slot) != 0L) {
            version.copyInto(
                patch,
                at,
                Versions.offset(slot),
                Versions.offset(slot) + Versions.STRIDE,
            )
            at += Versions.STRIDE
        }
        return patch
    }

    private fun apply(version: LongArray, patch: LongArray) {
        version[0] = patch[0]
        version[1] = patch[1]
        val mask = patch[2]
        var at = PATCH_HEADER
        for (slot in 0 until Versions.slots(version)) if (mask and (1L shl slot) != 0L) {
            patch.copyInto(version, Versions.offset(slot), at, at + Versions.STRIDE)
            at += Versions.STRIDE
        }
    }

    private companion object {
        /** Versions between two stored whole: the most patches a read applies. */
        const val KEYFRAME = 16
        const val PATCH_HEADER = 3
    }
}
