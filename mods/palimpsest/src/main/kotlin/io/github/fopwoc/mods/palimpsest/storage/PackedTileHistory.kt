package io.github.fopwoc.mods.palimpsest.storage

/**
 * Primitive record directory with one union coverage mask for each 64-layer group.
 *
 * Each layer costs one epoch, one packed record word (segment, offset, length, kind) and one
 * coverage reference; masks live in side arrays chosen by how many pixels a layer covers.
 */
@Suppress("TooManyFunctions")
internal class PackedTileHistory(private var ordered: Boolean) {
    private var epochs = LongArray(0)
    private var records = LongArray(0)
    private var coverageRefs = IntArray(0)
    private var packedPositions = LongArray(0)
    private var packedCount = 0
    private var denseMasks = LongArray(0)
    private var denseCount = 0
    private var inlineMasks = LongArray(0)
    private var inlineMode = false
    private var groupMasks = LongArray(0)

    var size = 0
        private set

    val lastEpoch: Long
        get() = if (size == 0) -1 else epochs[size - 1]

    val arrayBytes: Long
        get() =
            epochs.size.toLong() * Long.SIZE_BYTES +
                records.size.toLong() * Long.SIZE_BYTES +
                coverageRefs.size.toLong() * Int.SIZE_BYTES +
                packedPositions.size.toLong() * Long.SIZE_BYTES +
                denseMasks.size.toLong() * Long.SIZE_BYTES +
                inlineMasks.size.toLong() * Long.SIZE_BYTES +
                groupMasks.size.toLong() * Long.SIZE_BYTES

    fun epochAt(index: Int): Long = epochs[index]

    fun segmentAt(index: Int): Int = (records[index] ushr SEGMENT_SHIFT).toInt()

    fun offsetAt(index: Int): Long = (records[index] ushr OFFSET_SHIFT) and OFFSET_MASK

    fun lengthAt(index: Int): Int = ((records[index] ushr LENGTH_SHIFT) and LENGTH_MASK).toInt()

    fun kindAt(index: Int): Int = (records[index] and KIND_MASK).toInt()

    fun maskAt(index: Int, word: Int): Long {
        if (inlineMode) return inlineMasks[index * TileLayer.MASK_WORDS + word]
        val reference = coverageRefs[index]
        return when {
            reference < 0 -> denseMasks[(-reference - 1) * TileLayer.MASK_WORDS + word]
            reference >= PACKED_FLAG -> {
                var packed = packedPositions[reference and PACKED_INDEX_MASK]
                var result = 0L
                repeat(packedSize(reference)) {
                    val pixel = (packed and 255).toInt()
                    if (pixel ushr 6 == word) result = result or (1L shl (pixel and 63))
                    packed = packed ushr 8
                }
                result
            }
            reference == FULL_COVERAGE -> -1L
            reference ushr 6 == word -> 1L shl (reference and 63)
            else -> 0L
        }
    }

    fun groupMaskAt(group: Int, word: Int): Long = groupMasks[group * TileLayer.MASK_WORDS + word]

    fun isFullAt(index: Int): Boolean =
        (0 until TileLayer.MASK_WORDS).all { maskAt(index, it) == -1L }

    /** Records newer than the latest full-coverage layer, or all of them when there is none. */
    fun recordsSinceFull(): Int {
        for (index in size - 1 downTo 0) if (isFullAt(index)) return size - 1 - index
        return size
    }

    fun maskOf(index: Int): LongArray = LongArray(TileLayer.MASK_WORDS) { maskAt(index, it) }

    class Entry(
        val epoch: Long,
        val segment: Int,
        val offset: Long,
        val length: Int,
        val mask: LongArray,
        val kind: Int,
    )

    fun entryAt(index: Int): Entry =
        Entry(
            epochAt(index),
            segmentAt(index),
            offsetAt(index),
            lengthAt(index),
            maskOf(index),
            kindAt(index),
        )

    /** Swaps the records in [from, to) for [replacement], keeping whatever follows them. */
    fun replaceRange(from: Int, to: Int, replacement: List<Entry>) {
        require(ordered && from in 0..to && to <= size)
        val tail = (to until size).map(::entryAt)
        truncate(from)
        for (entry in replacement + tail) {
            add(entry.epoch, entry.segment, entry.offset, entry.length, entry.mask, entry.kind)
        }
    }

    fun add(epoch: Long, segment: Int, offset: Long, length: Int, mask: LongArray, kind: Int = 0) {
        if (ordered) require(epoch > lastEpoch)
        require(segment in 0..LOG_SEGMENT_B && offset in 0..OFFSET_MASK)
        require(length in 1..LENGTH_MASK && kind in 0..KIND_MASK)
        ensureCapacity(size + 1)
        epochs[size] = epoch
        records[size] =
            (segment.toLong() shl SEGMENT_SHIFT) or
                (offset shl OFFSET_SHIFT) or
                (length.toLong() shl LENGTH_SHIFT) or
                kind.toLong()
        if (inlineMode) {
            mask.copyInto(inlineMasks, size * TileLayer.MASK_WORDS)
        } else {
            val covered = mask.sumOf(java.lang.Long::bitCount)
            require(covered in 1..TileLayer.PIXELS)
            coverageRefs[size] =
                when (covered) {
                    1 -> {
                        val word = mask.indexOfFirst { it != 0L }
                        word * Long.SIZE_BITS + java.lang.Long.numberOfTrailingZeros(mask[word])
                    }
                    TileLayer.PIXELS -> FULL_COVERAGE
                    in 2..8 -> {
                        require(packedCount <= PACKED_INDEX_MASK)
                        ensurePackedCapacity(packedCount + 1)
                        var packed = 0L
                        var at = 0
                        for (word in mask.indices) {
                            var remaining = mask[word]
                            while (remaining != 0L) {
                                val pixel =
                                    word * Long.SIZE_BITS +
                                        java.lang.Long.numberOfTrailingZeros(remaining)
                                packed = packed or (pixel.toLong() shl (at++ * 8))
                                remaining = remaining and (remaining - 1)
                            }
                        }
                        packedPositions[packedCount] = packed
                        PACKED_FLAG or ((covered - 2) shl PACKED_COUNT_SHIFT) or packedCount++
                    }
                    else -> {
                        ensureDenseCapacity(denseCount + 1)
                        mask.copyInto(denseMasks, denseCount * TileLayer.MASK_WORDS)
                        -(denseCount++ + 1)
                    }
                }
        }
        if (ordered) addToGroup(size)
        size++
        if (
            ordered && !inlineMode && size % GROUP_SIZE == 0 && adaptiveCoverageBytes() > size * 32L
        ) {
            useInlineMasks()
        }
    }

    /**
     * Sorts reloaded records by epoch and drops duplicate epochs deterministically: of two records
     * at one epoch the one from the segment with the lower [segmentRank] wins, so every machine
     * that holds the same segment files resolves a merge identically. Returns how many records were
     * dropped.
     */
    fun finishReload(segmentRank: (Int) -> Int = { it }): Int {
        if (ordered) return 0
        if (size > 1) sort(0, size - 1)
        var kept = 0
        for (position in 0 until size) {
            if (kept > 0 && epochs[kept - 1] == epochs[position]) {
                if (segmentRank(segmentAt(position)) < segmentRank(segmentAt(kept - 1))) {
                    copyEntry(position, kept - 1)
                }
                continue
            }
            if (kept != position) copyEntry(position, kept)
            kept++
        }
        val dropped = size - kept
        size = kept
        epochs = epochs.copyOf(size)
        records = records.copyOf(size)
        coverageRefs = coverageRefs.copyOf(size)
        packedPositions = packedPositions.copyOf(packedCount)
        denseMasks = denseMasks.copyOf(denseCount * TileLayer.MASK_WORDS)
        if (adaptiveCoverageBytes() > size * 32L) useInlineMasks()
        rebuildGroups(0)
        ordered = true
        return dropped
    }

    /** Drops the newest records so that [newSize] remain; side arrays are left to be reused. */
    fun truncate(newSize: Int) {
        require(ordered && newSize in 0..size)
        if (newSize == size) return
        size = newSize
        rebuildGroups(newSize / GROUP_SIZE)
    }

    private fun rebuildGroups(fromGroup: Int) {
        val required = groupCount() * TileLayer.MASK_WORDS
        if (groupMasks.size != required) groupMasks = groupMasks.copyOf(required)
        groupMasks.fill(0L, fromGroup * TileLayer.MASK_WORDS, required)
        for (position in fromGroup * GROUP_SIZE until size) addToGroup(position)
    }

    private fun copyEntry(from: Int, to: Int) {
        epochs[to] = epochs[from]
        records[to] = records[from]
        if (inlineMode) {
            inlineMasks.copyInto(
                inlineMasks,
                to * TileLayer.MASK_WORDS,
                from * TileLayer.MASK_WORDS,
                (from + 1) * TileLayer.MASK_WORDS,
            )
        } else {
            coverageRefs[to] = coverageRefs[from]
        }
    }

    fun firstAfter(epoch: Long): Int {
        var left = 0
        var right = size
        while (left < right) {
            val middle = (left + right) ushr 1
            if (epochs[middle] <= epoch) left = middle + 1 else right = middle
        }
        return left
    }

    fun groupCanFill(group: Int, missing: LongArray): Boolean =
        (0 until TileLayer.MASK_WORDS).any { word ->
            groupMaskAt(group, word) and missing[word] != 0L
        }

    fun layerCanFill(position: Int, missing: LongArray): Boolean {
        if (inlineMode) {
            return (0 until TileLayer.MASK_WORDS).any { word ->
                inlineMasks[position * TileLayer.MASK_WORDS + word] and missing[word] != 0L
            }
        }
        val reference = coverageRefs[position]
        return when {
            reference < 0 ->
                (0 until TileLayer.MASK_WORDS).any { word ->
                    denseMasks[(-reference - 1) * TileLayer.MASK_WORDS + word] and missing[word] !=
                        0L
                }
            reference >= PACKED_FLAG -> {
                var packed = packedPositions[reference and PACKED_INDEX_MASK]
                repeat(packedSize(reference)) {
                    val pixel = (packed and 255).toInt()
                    if (missing[pixel ushr 6] and (1L shl (pixel and 63)) != 0L) return true
                    packed = packed ushr 8
                }
                false
            }
            reference == FULL_COVERAGE -> missing.any { it != 0L }
            else -> missing[reference ushr 6] and (1L shl (reference and 63)) != 0L
        }
    }

    private fun groupCount(): Int = (size + GROUP_SIZE - 1) / GROUP_SIZE

    private fun packedSize(reference: Int): Int = ((reference ushr PACKED_COUNT_SHIFT) and 7) + 2

    private fun addToGroup(position: Int) {
        val group = position / GROUP_SIZE
        val required = (group + 1) * TileLayer.MASK_WORDS
        if (groupMasks.size < required) groupMasks = groupMasks.copyOf(required)
        if (!inlineMode) {
            val reference = coverageRefs[position]
            if (reference >= PACKED_FLAG) {
                var packed = packedPositions[reference and PACKED_INDEX_MASK]
                repeat(packedSize(reference)) {
                    val pixel = (packed and 255).toInt()
                    val at = group * TileLayer.MASK_WORDS + (pixel ushr 6)
                    groupMasks[at] = groupMasks[at] or (1L shl (pixel and 63))
                    packed = packed ushr 8
                }
                return
            }
        }
        for (word in 0 until TileLayer.MASK_WORDS) {
            val at = group * TileLayer.MASK_WORDS + word
            groupMasks[at] = groupMasks[at] or maskAt(position, word)
        }
    }

    private fun ensureCapacity(required: Int) {
        if (epochs.size >= required) return
        val capacity = maxOf(required, epochs.size + maxOf(16, epochs.size / 2))
        epochs = epochs.copyOf(capacity)
        records = records.copyOf(capacity)
        if (inlineMode) inlineMasks = inlineMasks.copyOf(capacity * TileLayer.MASK_WORDS)
        else coverageRefs = coverageRefs.copyOf(capacity)
    }

    private fun adaptiveCoverageBytes(): Long =
        size.toLong() * Int.SIZE_BYTES +
            packedCount.toLong() * Long.SIZE_BYTES +
            denseCount.toLong() * TileLayer.MASK_WORDS * Long.SIZE_BYTES

    private fun useInlineMasks() {
        val inline = LongArray(epochs.size * TileLayer.MASK_WORDS)
        for (position in 0 until size) {
            for (word in 0 until TileLayer.MASK_WORDS) {
                inline[position * TileLayer.MASK_WORDS + word] = maskAt(position, word)
            }
        }
        inlineMasks = inline
        inlineMode = true
        coverageRefs = IntArray(0)
        packedPositions = LongArray(0)
        packedCount = 0
        denseMasks = LongArray(0)
        denseCount = 0
    }

    private fun ensureDenseCapacity(required: Int) {
        val capacity = denseMasks.size / TileLayer.MASK_WORDS
        if (capacity >= required) return
        val next = maxOf(required, capacity + maxOf(16, capacity / 2))
        denseMasks = denseMasks.copyOf(next * TileLayer.MASK_WORDS)
    }

    private fun ensurePackedCapacity(required: Int) {
        if (packedPositions.size >= required) return
        val next = maxOf(required, packedPositions.size + maxOf(16, packedPositions.size / 2))
        packedPositions = packedPositions.copyOf(next)
    }

    private fun sort(start: Int, end: Int) {
        var left = start
        var right = end
        val pivot = epochs[(start + end) ushr 1]
        while (left <= right) {
            while (epochs[left] < pivot) left++
            while (epochs[right] > pivot) right--
            if (left <= right) {
                swap(left, right)
                left++
                right--
            }
        }
        if (start < right) sort(start, right)
        if (left < end) sort(left, end)
    }

    private fun swap(a: Int, b: Int) {
        if (a == b) return
        val epoch = epochs[a]
        epochs[a] = epochs[b]
        epochs[b] = epoch
        val record = records[a]
        records[a] = records[b]
        records[b] = record
        val reference = coverageRefs[a]
        coverageRefs[a] = coverageRefs[b]
        coverageRefs[b] = reference
    }

    companion object {
        const val GROUP_SIZE = 64
        private const val KIND_BITS = 3
        private const val LENGTH_BITS = 9
        private const val OFFSET_BITS = 31
        private const val LENGTH_SHIFT = KIND_BITS
        private const val OFFSET_SHIFT = LENGTH_SHIFT + LENGTH_BITS
        private const val SEGMENT_SHIFT = OFFSET_SHIFT + OFFSET_BITS
        private const val KIND_MASK = (1L shl KIND_BITS) - 1
        private const val LENGTH_MASK = (1L shl LENGTH_BITS) - 1
        private const val OFFSET_MASK = (1L shl OFFSET_BITS) - 1
        /** Two sentinels above this address the write-ahead logs. */
        const val MAX_SEGMENT = (1 shl (Long.SIZE_BITS - SEGMENT_SHIFT)) - 3
        const val LOG_SEGMENT_A = MAX_SEGMENT + 1
        const val LOG_SEGMENT_B = MAX_SEGMENT + 2
        private const val FULL_COVERAGE = TileLayer.PIXELS
        private const val PACKED_FLAG = 1 shl 30
        private const val PACKED_COUNT_SHIFT = 27
        private const val PACKED_INDEX_MASK = (1 shl PACKED_COUNT_SHIFT) - 1

        fun forAppend(): PackedTileHistory = PackedTileHistory(ordered = true)

        fun forReload(): PackedTileHistory = PackedTileHistory(ordered = false)
    }
}
