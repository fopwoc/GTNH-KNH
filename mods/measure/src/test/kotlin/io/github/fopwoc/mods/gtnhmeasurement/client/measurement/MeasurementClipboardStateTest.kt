package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeasurementClipboardStateTest {
  @Test
  fun transformedClipboardOffsetsCopiedMeasurements() {
    val clipboardState = MeasurementClipboardState()
    clipboardState.copyFrom(
        originAnchor = anchorA,
        measurements = listOf(PersistedMeasurement(MeasurementMode.LINE, anchorA, anchorB)),
    )

    val transformed = clipboardState.transformedClipboard(anchorC)

    assertEquals(1, transformed.size)
    assertEquals(anchorC, transformed.single().first)
    assertEquals(anchorD, transformed.single().second)
  }

  @Test
  fun transformedClipboardResizesOnlyTargetAnchor() {
    val clipboardState = MeasurementClipboardState()
    clipboardState.resizeFrom(
        originAnchor = anchorB,
        measurement = MeasurementRecord(1L, MeasurementMode.LINE, anchorA, anchorB),
        resizeAnchorRole = MeasurementAnchorRole.SECOND,
    )

    val transformed = clipboardState.transformedClipboard(resizedAnchor)

    assertEquals(1, transformed.size)
    assertEquals(anchorA, transformed.single().first)
    assertEquals(resizedAnchor, transformed.single().second)
  }

  @Test
  fun beginPastePlacementUsesLastAnchorWhenDimensionsMatch() {
    val clipboardState = MeasurementClipboardState()
    clipboardState.copyFrom(
        originAnchor = anchorA,
        measurements = listOf(PersistedMeasurement(MeasurementMode.LINE, anchorA, anchorB)),
    )
    clipboardState.setLastAnchorInteraction(anchorC)

    assertTrue(clipboardState.beginPastePlacement())
    assertEquals(anchorC, clipboardState.pastePreviewAnchor)
    assertTrue(clipboardState.isPastePlacementActive)
  }

  private companion object {
    const val dimensionId = 0
    val anchorA = BlockSelection(0, 64, 0, dimensionId)
    val anchorB = BlockSelection(2, 64, 0, dimensionId)
    val anchorC = BlockSelection(10, 64, 0, dimensionId)
    val anchorD = BlockSelection(12, 64, 0, dimensionId)
    val resizedAnchor = BlockSelection(6, 65, 1, dimensionId)
  }
}
