package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementShortcutScheme as Keys
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode

/** Picks the hints for the current editing situation, most specific situation first. */
internal object MeasurementShortcutHudResolver {
  fun resolve(context: MeasurementShortcutHudContext): MeasurementShortcutHudModel? {
    if (!context.modeActive) {
      return null
    }

    val model =
        when {
          context.pastePlacementActive -> placement(context)
          context.hasDraftCreation -> draft(context)
          context.selectedMeasurementCount > 0 -> selection(context)
          context.hoveredMeasurementCount > 0 -> hovered(context)
          // Nothing going on: stay out of the way; the menu carries the full reference.
          else -> null
        }
    val reach = context.freecamReach ?: return model
    val reachHint =
        hint(
            "${Keys.editorModifierLabel()}+scroll",
            "freecam reach $reach (${Keys.selectionModifierLabel()} for ×8)",
            accent,
        )
    return model?.copy(hints = model.hints + reachHint)
        ?: MeasurementShortcutHudModel(title = "Freecam", hints = listOf(reachHint))
  }

  private fun placement(context: MeasurementShortcutHudContext) =
      MeasurementShortcutHudModel(
          title =
              when (context.clipboardOperation) {
                ClipboardOperation.MOVE -> "Move placement"
                ClipboardOperation.RESIZE -> "Resize placement"
                ClipboardOperation.COPY -> "Copy placement"
                ClipboardOperation.CUT -> "Cut placement"
                null -> "Placement"
              },
          hints =
              listOf(
                  hint(Keys.createClickLabel(), "place preview"),
                  hint(Keys.constraintModifierLabel(), "constrain movement to one axis", accent),
                  hint(Keys.selectionClickLabel(), "pick another measurement", secondary),
                  hint(Keys.cancelLabel(), "cancel", warning),
              ),
      )

  private fun draft(context: MeasurementShortcutHudContext): MeasurementShortcutHudModel {
    val sphere = context.selectedMode == MeasurementMode.SPHERE
    return MeasurementShortcutHudModel(
        title =
            when {
              sphere && context.draftHasPreview -> "Draft sphere"
              sphere -> "Choose radius anchor"
              context.draftHasPreview -> "Draft measurement"
              else -> "Choose second anchor"
            },
        hints =
            buildList {
              add(hint(Keys.createClickLabel(), "confirm measurement"))
              add(hint(Keys.targetedCreateClickLabel(), "place against block face", secondary))
              if (context.selectedMode == MeasurementMode.LINE) {
                add(hint(Keys.constraintModifierLabel(), "constrain line to 90°", accent))
              }
              if (sphere) {
                add(hint("", "first anchor is center · second anchor sets radius", accent))
              }
              add(hint("${Keys.cancelLabel()} / ${Keys.deleteLabel()}", "cancel draft", warning))
            },
    )
  }

  private fun selection(context: MeasurementShortcutHudContext) =
      MeasurementShortcutHudModel(
          title =
              if (context.selectedMeasurementCount == 1) "1 measurement selected"
              else "${context.selectedMeasurementCount} measurements selected",
          hints =
              listOf(
                  hint(Keys.transformClickLabel(), "move selected"),
                  hint(Keys.editClipboardKeys(), "copy / cut / paste clipboard", secondary),
                  hint(Keys.deleteLabel(), "delete", warning),
                  hint("${Keys.undoLabel()} / ${Keys.redoLabel()}", "undo / redo", secondary),
                  hint(Keys.cancelLabel(), "clear selection", secondary),
              ),
      )

  private fun hovered(context: MeasurementShortcutHudContext) =
      MeasurementShortcutHudModel(
          title =
              if (context.hoveredMeasurementCount == 1) "Measurement under cursor"
              else "${context.hoveredMeasurementCount} measurements under cursor",
          hints =
              listOf(
                  hint(Keys.selectionClickLabel(), "select"),
                  hint(Keys.multiSelectionClickLabel(), "add all at anchor", secondary),
                  hint(Keys.transformClickLabel(), "move or resize", accent),
                  hint(Keys.createClickLabel(), "start a new measurement here", secondary),
              ),
      )

  private val secondary = MeasurementShortcutHudPalette.Secondary
  private val accent = MeasurementShortcutHudPalette.Accent
  private val warning = MeasurementShortcutHudPalette.Warning

  private fun hint(
      keys: String,
      action: String,
      color: Color = MeasurementShortcutHudPalette.Primary,
  ) = MeasurementShortcutHudHint(keys, action, color)
}
