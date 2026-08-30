package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementShortcutHudResolverTest {
  @Test
  fun hiddenWhenModeIsInactive() {
    val model =
        MeasurementShortcutHudResolver.resolve(
            MeasurementShortcutHudContext(
                modeActive = false,
                selectedMode = MeasurementMode.LINE,
                selectedMeasurementCount = 1,
                hoveredMeasurementCount = 1,
                hasDraftCreation = true,
                draftHasPreview = true,
                clipboardOperation = ClipboardOperation.COPY,
                pastePlacementActive = true,
            )
        )

    assertNull(model)
  }

  @Test
  fun pastePlacementHasHighestPriority() {
    val model =
        MeasurementShortcutHudResolver.resolve(
            MeasurementShortcutHudContext(
                modeActive = true,
                selectedMode = MeasurementMode.LINE,
                selectedMeasurementCount = 2,
                hoveredMeasurementCount = 1,
                hasDraftCreation = true,
                draftHasPreview = true,
                clipboardOperation = ClipboardOperation.MOVE,
                pastePlacementActive = true,
            )
        )

    assertNotNull(model)
    assertEquals("Move placement", model.title)
    assertTrue(model.hints.first().text.contains("place preview"))
    assertTrue(model.hints.any { it.text.contains("constrain movement") })
  }

  @Test
  fun draftHasPriorityOverSelectionAndHover() {
    val model =
        MeasurementShortcutHudResolver.resolve(
            MeasurementShortcutHudContext(
                modeActive = true,
                selectedMode = MeasurementMode.LINE,
                selectedMeasurementCount = 3,
                hoveredMeasurementCount = 2,
                hasDraftCreation = true,
                draftHasPreview = false,
                clipboardOperation = null,
                pastePlacementActive = false,
            )
        )

    assertNotNull(model)
    assertEquals("Choose second anchor", model.title)
    assertTrue(model.hints.any { it.text.contains("cancel draft") })
    assertTrue(model.hints.any { it.text.contains("90°") })
  }

  @Test
  fun selectionHasPriorityOverHover() {
    val model =
        MeasurementShortcutHudResolver.resolve(
            MeasurementShortcutHudContext(
                modeActive = true,
                selectedMode = MeasurementMode.LINE,
                selectedMeasurementCount = 2,
                hoveredMeasurementCount = 1,
                hasDraftCreation = false,
                draftHasPreview = false,
                clipboardOperation = null,
                pastePlacementActive = false,
            )
        )

    assertNotNull(model)
    assertEquals("2 measurements selected", model.title)
    assertTrue(model.hints.any { it.text.contains("clipboard") })
  }

  @Test
  fun hoverShowsSelectionAndTransformHints() {
    val model =
        MeasurementShortcutHudResolver.resolve(
            MeasurementShortcutHudContext(
                modeActive = true,
                selectedMode = MeasurementMode.LINE,
                selectedMeasurementCount = 0,
                hoveredMeasurementCount = 1,
                hasDraftCreation = false,
                draftHasPreview = false,
                clipboardOperation = null,
                pastePlacementActive = false,
            )
        )

    assertNotNull(model)
    assertEquals("Measurement under cursor", model.title)
    assertTrue(
        model.hints.any { it.text.contains(MeasurementShortcutScheme.selectionClickLabel()) }
    )
    assertTrue(
        model.hints.any { it.text.contains(MeasurementShortcutScheme.transformClickLabel()) }
    )
  }

  @Test
  fun sphereDraftExplainsCenterAndRadiusAnchors() {
    val model =
        MeasurementShortcutHudResolver.resolve(
            MeasurementShortcutHudContext(
                modeActive = true,
                selectedMode = MeasurementMode.SPHERE,
                selectedMeasurementCount = 0,
                hoveredMeasurementCount = 0,
                hasDraftCreation = true,
                draftHasPreview = false,
                clipboardOperation = null,
                pastePlacementActive = false,
            )
        )

    assertNotNull(model)
    assertEquals("Choose radius anchor", model.title)
    assertTrue(model.hints.any { it.text.contains("center") })
    assertTrue(model.hints.any { it.text.contains("radius") })
  }
}
