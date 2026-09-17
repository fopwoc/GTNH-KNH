package io.github.fopwoc.mods.palimpsest.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackedTileHistoryTest {
  @Test
  fun compactCoverageReferencesSurviveSortAndFurtherAppends() {
    val history = PackedTileHistory.forReload()
    history.add(3, 0, 3, 5, longArrayOf(1L shl 1, 0, 1L shl 2, 0))
    history.add(2, 0, 2, 258, LongArray(TileLayer.MASK_WORDS) { -1L })
    history.add(1, 0, 1, 5, longArrayOf(0, 0, 0, 1L shl 8))
    history.finishReload()

    assertEquals(1L shl 8, history.maskAt(0, 3))
    assertEquals(0L, history.maskAt(0, 0))
    assertEquals(-1L, history.maskAt(1, 2))
    assertEquals(1L shl 1, history.maskAt(2, 0))
    assertEquals(1L shl 2, history.maskAt(2, 2))
    assertTrue(history.layerCanFill(2, longArrayOf(0, 0, 1L shl 2, 0)))
    assertTrue(!history.layerCanFill(2, longArrayOf(0, 0, 0, 1L shl 8)))

    history.add(4, 0, 4, 6, longArrayOf(0, 1L shl 3, 0, 1L shl 4))
    assertEquals(1L shl 4, history.maskAt(3, 3))
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
