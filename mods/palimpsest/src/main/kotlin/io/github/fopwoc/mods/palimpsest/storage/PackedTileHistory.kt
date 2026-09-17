package io.github.fopwoc.mods.palimpsest.storage

import java.io.IOException

/** Primitive record directory with one union coverage mask for each 64-layer group. */
internal class PackedTileHistory(private var ordered: Boolean) {
  private var epochs = LongArray(0)
  private var segments = IntArray(0)
  private var offsets = LongArray(0)
  private var lengths = IntArray(0)
  private var coverageRefs = IntArray(0)
  private var denseMasks = LongArray(0)
  private var denseCount = 0
  private var groupMasks = LongArray(0)

  var size = 0
    private set

  val lastEpoch: Long
    get() = if (size == 0) -1 else epochs[size - 1]

  val arrayBytes: Long
    get() =
        epochs.size.toLong() * Long.SIZE_BYTES +
            segments.size.toLong() * Int.SIZE_BYTES +
            offsets.size.toLong() * Long.SIZE_BYTES +
            lengths.size.toLong() * Int.SIZE_BYTES +
            coverageRefs.size.toLong() * Int.SIZE_BYTES +
            denseMasks.size.toLong() * Long.SIZE_BYTES +
            groupMasks.size.toLong() * Long.SIZE_BYTES

  fun epochAt(index: Int): Long = epochs[index]

  fun segmentAt(index: Int): Int = segments[index]

  fun offsetAt(index: Int): Long = offsets[index]

  fun lengthAt(index: Int): Int = lengths[index]

  fun maskAt(index: Int, word: Int): Long {
    val reference = coverageRefs[index]
    return when {
      reference < 0 -> denseMasks[(-reference - 1) * TileLayer.MASK_WORDS + word]
      reference == FULL_COVERAGE -> -1L
      reference ushr 6 == word -> 1L shl (reference and 63)
      else -> 0L
    }
  }

  fun groupMaskAt(group: Int, word: Int): Long = groupMasks[group * TileLayer.MASK_WORDS + word]

  fun add(epoch: Long, segment: Int, offset: Long, length: Int, mask: LongArray) {
    if (ordered) require(epoch > lastEpoch)
    ensureCapacity(size + 1)
    epochs[size] = epoch
    segments[size] = segment
    offsets[size] = offset
    lengths[size] = length
    val covered = mask.sumOf(java.lang.Long::bitCount)
    require(covered in 1..TileLayer.PIXELS)
    coverageRefs[size] =
        when (covered) {
          1 -> {
            val word = mask.indexOfFirst { it != 0L }
            word * Long.SIZE_BITS + java.lang.Long.numberOfTrailingZeros(mask[word])
          }
          TileLayer.PIXELS -> FULL_COVERAGE
          else -> {
            ensureDenseCapacity(denseCount + 1)
            mask.copyInto(denseMasks, denseCount * TileLayer.MASK_WORDS)
            -(denseCount++ + 1)
          }
        }
    if (ordered) addToGroup(size)
    size++
  }

  fun finishReload() {
    if (ordered) return
    if (size > 1) sort(0, size - 1)
    for (position in 1 until size) {
      if (epochs[position - 1] == epochs[position]) {
        throw IOException("Duplicate tile epoch in sealed segments")
      }
    }
    epochs = epochs.copyOf(size)
    segments = segments.copyOf(size)
    offsets = offsets.copyOf(size)
    lengths = lengths.copyOf(size)
    coverageRefs = coverageRefs.copyOf(size)
    denseMasks = denseMasks.copyOf(denseCount * TileLayer.MASK_WORDS)
    groupMasks = LongArray(groupCount() * TileLayer.MASK_WORDS)
    for (position in 0 until size) addToGroup(position)
    ordered = true
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
    val reference = coverageRefs[position]
    return when {
      reference < 0 ->
          (0 until TileLayer.MASK_WORDS).any { word ->
            denseMasks[(-reference - 1) * TileLayer.MASK_WORDS + word] and missing[word] != 0L
          }
      reference == FULL_COVERAGE -> missing.any { it != 0L }
      else -> missing[reference ushr 6] and (1L shl (reference and 63)) != 0L
    }
  }

  private fun groupCount(): Int = (size + GROUP_SIZE - 1) / GROUP_SIZE

  private fun addToGroup(position: Int) {
    val group = position / GROUP_SIZE
    val required = (group + 1) * TileLayer.MASK_WORDS
    if (groupMasks.size < required) groupMasks = groupMasks.copyOf(required)
    for (word in 0 until TileLayer.MASK_WORDS) {
      val at = group * TileLayer.MASK_WORDS + word
      groupMasks[at] = groupMasks[at] or maskAt(position, word)
    }
  }

  private fun ensureCapacity(required: Int) {
    if (epochs.size >= required) return
    val capacity = maxOf(required, epochs.size + maxOf(16, epochs.size / 2))
    epochs = epochs.copyOf(capacity)
    segments = segments.copyOf(capacity)
    offsets = offsets.copyOf(capacity)
    lengths = lengths.copyOf(capacity)
    coverageRefs = coverageRefs.copyOf(capacity)
  }

  private fun ensureDenseCapacity(required: Int) {
    val capacity = denseMasks.size / TileLayer.MASK_WORDS
    if (capacity >= required) return
    val next = maxOf(required, capacity + maxOf(16, capacity / 2))
    denseMasks = denseMasks.copyOf(next * TileLayer.MASK_WORDS)
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
    val segment = segments[a]
    segments[a] = segments[b]
    segments[b] = segment
    val offset = offsets[a]
    offsets[a] = offsets[b]
    offsets[b] = offset
    val length = lengths[a]
    lengths[a] = lengths[b]
    lengths[b] = length
    val reference = coverageRefs[a]
    coverageRefs[a] = coverageRefs[b]
    coverageRefs[b] = reference
  }

  companion object {
    const val GROUP_SIZE = 64
    private const val FULL_COVERAGE = TileLayer.PIXELS

    fun forAppend(): PackedTileHistory = PackedTileHistory(ordered = true)

    fun forReload(): PackedTileHistory = PackedTileHistory(ordered = false)
  }
}
