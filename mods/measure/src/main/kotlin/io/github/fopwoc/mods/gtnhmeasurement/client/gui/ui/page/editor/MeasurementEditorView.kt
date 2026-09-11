package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.page.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.fopwoc.mods.framework.ui.compose.component.SegmentedControlDefaults
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.component.native.MultiSelectableList
import io.github.fopwoc.mods.framework.ui.compose.component.native.TextField
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.LazyColumn
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.items
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.component.MeasurementBodyText
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.component.MeasurementScaffold
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.component.MeasurementSection
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.component.ShortcutRow
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.theme.MeasurementPalette
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode

@Composable
fun MeasurementEditorView(
    state: MeasurementEditorModel,
    screenWidth: Int = 0,
    screenHeight: Int = 0,
    onSelectMode: (MeasurementMode) -> Unit = {},
    onSelectEntries: (Set<Int>) -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    exportName: TextFieldState = remember { TextFieldState() },
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    onMoveSelection: () -> Unit = {},
    onClose: () -> Unit = {},
) {
  MeasurementScaffold(
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      title = "Measure",
      subtitle =
          if (state.clipboardLabel.isEmpty()) state.contextLabel
          else "${state.contextLabel} · ${state.clipboardLabel}",
      onClose = onClose,
  ) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = VerticalArrangement.spacedBy(4.uu),
    ) {
      ModeRow(state, onSelectMode)

      Row(
          modifier = Modifier.fillMaxWidth().weight(1f),
          horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
      ) {
        MeasurementSection(
            title = "Measurements",
            modifier = Modifier.weight(3f).fillMaxHeight(),
            elevated = true,
        ) {
          Column(
              modifier = Modifier.fillMaxSize(),
              verticalArrangement = VerticalArrangement.spacedBy(4.uu),
          ) {
            if (state.entries.isEmpty()) {
              MeasurementBodyText(
                  text = "Nothing measured in this dimension yet.",
                  modifier = Modifier.fillMaxWidth().weight(1f),
                  color = MeasurementPalette.Muted,
              )
            } else {
              MultiSelectableList(
                  items = state.entries.map(MeasurementEntry::label),
                  selectedIndices = state.selectedEntryIndices,
                  modifier =
                      Modifier.fillMaxWidth()
                          .weight(1f)
                          .tooltip(
                              "Click selects · Ctrl+click toggles · Shift+click extends · Ctrl/Cmd+A all"
                          ),
                  rowHeight = 12.uu,
                  onSelectionChange = onSelectEntries,
              )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
                verticalAlignment = VerticalAlignment.CENTER,
            ) {
              Button(
                  text = if (state.selectedCount > 1) "Delete ${state.selectedCount}" else "Delete",
                  modifier = Modifier.weight(1f),
                  enabled = state.selectedCount > 0,
                  onClick = onDeleteSelected,
              )
              Button(
                  text = "Move",
                  modifier =
                      Modifier.weight(1f)
                          .tooltip(
                              "Pick the selection up as one batch and drop it at the crosshair"
                          ),
                  enabled = state.selectedCount > 0,
                  onClick = onMoveSelection,
              )
              Button(
                  text = "Deselect",
                  modifier = Modifier.weight(1f),
                  enabled = state.selectedCount > 0,
                  onClick = onClearSelection,
              )
              Button(
                  text = "Undo",
                  modifier = Modifier.weight(1f),
                  enabled = state.canUndo,
                  onClick = onUndo,
              )
              Button(
                  text = "Redo",
                  modifier = Modifier.weight(1f),
                  enabled = state.canRedo,
                  onClick = onRedo,
              )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
                verticalAlignment = VerticalAlignment.CENTER,
            ) {
              TextField(
                  state = exportName,
                  modifier =
                      Modifier.weight(2f)
                          .tooltip(
                              "Export writes the selection (or the whole dimension) to config/measure/exports; import merges a set into this world"
                          ),
                  placeholder = "export name",
              )
              Button(text = "Export", modifier = Modifier.weight(1f), onClick = onExport)
              Button(text = "Import", modifier = Modifier.weight(1f), onClick = onImport)
            }
            if (state.exchangeMessage.isNotEmpty()) {
              MeasurementBodyText(text = state.exchangeMessage, color = MeasurementPalette.Muted)
            }
          }
        }

        MeasurementSection(title = "Shortcuts", modifier = Modifier.weight(2f).fillMaxHeight()) {
          LazyColumn(modifier = Modifier.fillMaxSize(), itemHeight = 14.uu) {
            items(state.shortcuts) { reference ->
              ShortcutRow(keys = reference.keys, action = reference.action)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ModeRow(state: MeasurementEditorModel, onSelectMode: (MeasurementMode) -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
      verticalAlignment = VerticalAlignment.CENTER,
  ) {
    state.availableModes.forEach { mode ->
      val label = if (mode.isEnabled) mode.displayName else "Off"
      val selected = mode == state.selectedMode
      Button(
          text =
              if (selected) SegmentedControlDefaults.selectedLabel(label) else StyledText.of(label),
          modifier = Modifier.weight(1f),
          onClick = { if (!selected) onSelectMode(mode) },
      )
    }
  }
}
