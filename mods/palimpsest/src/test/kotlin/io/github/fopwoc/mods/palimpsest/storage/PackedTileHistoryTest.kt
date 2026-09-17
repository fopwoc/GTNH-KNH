package io.github.fopwoc.mods.palimpsest.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackedTileHistoryTest {
  @Test
  fun denseHistoriesUseInlineMasksAfterAppendAndReload() {
    val mask = longArrayOf(0x3FF, 0, 0, 0)
    val appended = PackedTileHistory.forAppend()
    repeat(65) { epoch -> appended.add(epoch.toLong(), 0, epoch.toLong(), 42, mask) }
    assertEquals(0x3FF, appended.maskAt(64, 0))
    assertEquals(0x3FF, appended.groupMaskAt(1, 0))

    val reloaded = PackedTileHistory.forReload()
    for (epoch in 64 downTo 0) reloaded.add(epoch.toLong(), 0, epoch.toLong(), 42, mask)
    reloaded.finishReload()
    assertEquals(0x3FF, reloaded.maskAt(64, 0))
    assertEquals(0x3FF, reloaded.groupMaskAt(1, 0))
    assertTrue(reloaded.arrayBytes <= appended.arrayBytes)
  }

  @Test
  fun compactCoverageReferencesSurviveSortAndFurtherAppends() {
    val history = PackedTileHistory.forReload()
    history.add(3, 0, 3, 5, longArrayOf(1L shl 1, 0, 1L shl 2, 0))
    history.add(2, 0, 2, 258, LongArray(TileLayer.MASK_WORDS) { -1L })
    history.add(1, 0, 1, 5, longArrayOf(0, 0, 0, 1L shl 8))
    history.add(4, 0, 4, 42, longArrayOf(0x1FF, 0, 1, 0))
    history.finishReload()

    assertEquals(1L shl 8, history.maskAt(0, 3))
    assertEquals(0L, history.maskAt(0, 0))
    assertEquals(-1L, history.maskAt(1, 2))
    assertEquals(1L shl 1, history.maskAt(2, 0))
    assertEquals(1L shl 2, history.maskAt(2, 2))
    assertTrue(history.layerCanFill(2, longArrayOf(0, 0, 1L shl 2, 0)))
    assertTrue(!history.layerCanFill(2, longArrayOf(0, 0, 0, 1L shl 8)))
    assertEquals(0x1FF, history.maskAt(3, 0))
    assertEquals(1, history.maskAt(3, 2))

    history.add(5, 0, 5, 14, longArrayOf(3, 3, 3, 3))
    assertEquals(3L, history.maskAt(4, 3))
    assertTrue(history.layerCanFill(4, longArrayOf(0, 0, 0, 1L shl 1)))
    assertEquals(-1L, history.groupMaskAt(0, 3))
  }

  @Test
  fun millionLayerDirectoriesKeepArrayStorageBounded() {
    val mask = longArrayOf(1, 0, 0, 0)
    var bytes = 0L
    repeat(1024) {
      val history = PackedTileHistory.forAppend()
      repeat(1024) { epoch -> history.add(epoch.toLong(), 0, epoch.toLong(), 49, mask) }
      bytes += history.arrayBytes
      assertEquals(1024, history.firstAfter(1023))
    }
    assertTrue(bytes < 40L * 1024 * 1024, "Packed arrays occupied $bytes bytes")
  }

  @Test
  fun sortsLargeReverseChronologicalHistoryAndBuildsCoverageGroups() {
    val history = PackedTileHistory.forReload()
    for (epoch in 65_535 downTo 0) {
      val coverage = LongArray(TileLayer.MASK_WORDS)
      val pixel = epoch and 255
      coverage[pixel ushr 6] = 1L shl (pixel and 63)
      history.add(epoch.toLong(), 0, epoch.toLong(), 49, coverage)
    }
    history.finishReload()
    assertEquals(65_536, history.size)
    assertEquals(0, history.firstAfter(-1))
    assertEquals(1, history.firstAfter(0))
    assertEquals(65_536, history.firstAfter(65_535))
    assertEquals(-1L, history.groupMaskAt(0, 0))
    assertEquals(0L, history.groupMaskAt(0, 1))
    assertTrue(history.arrayBytes < 2_000_000)

    val finalMask = longArrayOf(1, 0, 0, 0)
    history.add(65_536, 0, 65_536, 49, finalMask)
    assertEquals(65_537, history.size)
    assertEquals(1L, history.groupMaskAt(1024, 0))
  }
}
