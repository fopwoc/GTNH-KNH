package io.github.fopwoc.mods.palimpsest.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackedTileHistoryTest {
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
    assertTrue(bytes < 80L * 1024 * 1024, "Packed arrays occupied $bytes bytes")
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
    assertTrue(history.arrayBytes < 4_000_000)

    val finalMask = longArrayOf(1, 0, 0, 0)
    history.add(65_536, 0, 65_536, 49, finalMask)
    assertEquals(65_537, history.size)
    assertEquals(1L, history.groupMaskAt(1024, 0))
  }
}
