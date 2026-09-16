package io.github.fopwoc.mods.framework.ui.compose.canvas

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class PixelCanvasTest {
  @Test
  fun writesCopySourceRowsAndExposeTheDirtyTile() {
    val canvas = PixelCanvas(4, 3)
    canvas.acknowledge(canvas.snapshotDirty()!!)

    val source = intArrayOf(99, 1, 2, 99, 3, 4, 99)
    canvas.write(PixelRegion(1, 1, 2, 2), source, offset = 1, stride = 3)
    source.fill(0)

    val snapshot = canvas.snapshotDirty()!!
    assertEquals(PixelRegion(0, 0, 4, 3), snapshot.region)
    assertContentEquals(intArrayOf(0, 0, 0, 0, 0, 1, 2, 0, 0, 3, 4, 0), snapshot.argb)
    canvas.acknowledge(snapshot)
    assertNull(canvas.snapshotDirty())
  }

  @Test
  fun acknowledgesOnlyTheRevisionThatWasUploaded() {
    val canvas = PixelCanvas(4, 4)
    val initial = canvas.snapshotDirty()!!
    canvas.acknowledge(initial)

    canvas.fill(PixelRegion(0, 0, 1, 1), 7)
    val uploading = canvas.snapshotDirty()!!
    canvas.fill(PixelRegion(3, 3, 1, 1), 9)
    canvas.acknowledge(uploading)

    val pending = canvas.snapshotDirty()!!
    assertEquals(PixelRegion(0, 0, 4, 4), pending.region)
    assertEquals(7, pending.argb[0])
    assertEquals(9, pending.argb.last())
  }

  @Test
  fun distantWritesProduceSeparateTiles() {
    val canvas = PixelCanvas(32, 16)
    canvas.acknowledge(canvas.snapshotDirty()!!)
    canvas.acknowledge(canvas.snapshotDirty()!!)

    canvas.fill(PixelRegion(0, 0, 1, 1), 7)
    canvas.fill(PixelRegion(31, 15, 1, 1), 9)

    val first = canvas.snapshotDirty()!!
    assertEquals(PixelRegion(0, 0, 16, 16), first.region)
    canvas.acknowledge(first)
    val second = canvas.snapshotDirty()!!
    assertEquals(PixelRegion(16, 0, 16, 16), second.region)
    canvas.acknowledge(second)
    assertNull(canvas.snapshotDirty())
  }

  @Test
  fun failedUploadRemainsPendingAndAnotherCanvasCannotAcknowledgeIt() {
    val canvas = PixelCanvas(16, 16)
    val snapshot = canvas.snapshotDirty()!!
    assertFailsWith<IllegalArgumentException> { PixelCanvas(16, 16).acknowledge(snapshot) }
    assertContentEquals(snapshot.argb, canvas.snapshotDirty()!!.argb)
    canvas.acknowledge(snapshot)
    assertNull(canvas.snapshotDirty())
  }
}
