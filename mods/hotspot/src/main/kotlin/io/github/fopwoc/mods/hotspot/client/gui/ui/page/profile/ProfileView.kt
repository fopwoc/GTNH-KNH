package io.github.fopwoc.mods.hotspot.client.gui.ui.page.profile

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.Dialog
import io.github.fopwoc.mods.framework.ui.compose.component.Scaffold
import io.github.fopwoc.mods.framework.ui.compose.component.Section
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.component.native.MultiSelectableList
import io.github.fopwoc.mods.framework.ui.compose.component.native.SelectableList
import io.github.fopwoc.mods.framework.ui.compose.component.native.Slider
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.theme.MinecraftTheme
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun ProfileView(
    state: ProfileModel,
    screenWidth: Int = 0,
    screenHeight: Int = 0,
    onProfile: () -> Unit = {},
    onDurationChange: (Int) -> Unit = {},
    onPreviousDimension: () -> Unit = {},
    onNextDimension: () -> Unit = {},
    onFocusChunk: (Int) -> Unit = {},
    onSelectTileEntities: (Set<Int>) -> Unit = {},
    onClear: () -> Unit = {},
    onClose: () -> Unit = {},
) {
  state.blocker?.let { blocker ->
    Dialog(title = "Hotspot", text = blocker, onButton = onClose)
    return
  }
  Scaffold(
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      title = "Hotspot",
      subtitle = state.statusLine,
      onClose = onClose,
  ) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = VerticalArrangement.spacedBy(4.uu),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
          verticalAlignment = VerticalAlignment.CENTER,
      ) {
        Slider(
            value = state.durationSeconds.toDouble(),
            onValueChange = { onDurationChange(it.toInt()) },
            modifier = Modifier.weight(2f),
            valueRange = 1.0..state.maxDurationSeconds.toDouble(),
            label = "Window",
            suffix = " s",
            showDecimal = false,
        )
        Button(
            text = "Profile",
            modifier =
                Modifier.weight(1f).tooltip("Ask the server to profile for the chosen window"),
            enabled = state.canProfile,
            onClick = onProfile,
        )
        Button(
            text = "Deselect",
            modifier = Modifier.weight(1f).tooltip("Drop every highlight; the snapshot stays"),
            enabled = state.hasSelection,
            onClick = onClear,
        )
      }

      if (!state.hasSnapshot) {
        Text(
            text = state.emptyHint,
            modifier = Modifier.fillMaxWidth().weight(1f),
            style = MinecraftTheme.typography.muted.copy(wrap = true),
        )
        return@Column
      }

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
          verticalAlignment = VerticalAlignment.CENTER,
      ) {
        Button(
            text = "<",
            modifier = Modifier.width(16.uu),
            enabled = state.canCycleDimensions,
            onClick = onPreviousDimension,
        )
        Text(
            text = state.dimensionLabel,
            modifier = Modifier.weight(1f),
            style = MinecraftTheme.typography.body,
        )
        Button(
            text = ">",
            modifier = Modifier.width(16.uu),
            enabled = state.canCycleDimensions,
            onClick = onNextDimension,
        )
      }
      Text(
          text = state.dimensionSummary,
          modifier = Modifier.fillMaxWidth(),
          style = MinecraftTheme.typography.muted,
      )

      Row(
          modifier = Modifier.fillMaxWidth().weight(1f),
          horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
      ) {
        Section(
            title = "Chunks",
            modifier = Modifier.weight(1f).fillMaxHeight(),
            elevated = true,
        ) {
          SelectableList(
              items = state.chunks.map(ChunkRow::label),
              selectedIndex = state.focusedChunkIndex,
              modifier =
                  Modifier.fillMaxSize()
                      .weight(1f)
                      .tooltip("Click a chunk to highlight it and list what ticks inside"),
              rowHeight = 12.uu,
              onSelectedIndexChange = onFocusChunk,
          )
        }
        Section(
            title = "Tile entities",
            modifier = Modifier.weight(1f).fillMaxHeight(),
            elevated = true,
        ) {
          if (state.focusedChunkIndex < 0) {
            Text(
                text = "Pick a chunk on the left.",
                modifier = Modifier.fillMaxWidth().weight(1f),
                style = MinecraftTheme.typography.muted.copy(wrap = true),
            )
          } else if (state.tileEntities.isEmpty()) {
            Text(
                text = "Nothing above the server's listing threshold in this chunk.",
                modifier = Modifier.fillMaxWidth().weight(1f),
                style = MinecraftTheme.typography.muted.copy(wrap = true),
            )
          } else {
            MultiSelectableList(
                items = state.tileEntities.map(TileEntityRow::label),
                selectedIndices = state.selectedTileEntityIndices,
                modifier =
                    Modifier.fillMaxWidth()
                        .weight(1f)
                        .tooltip(
                            "Click selects · Ctrl+click toggles · Shift+click extends · Ctrl/Cmd+A all"
                        ),
                rowHeight = 12.uu,
                onSelectionChange = onSelectTileEntities,
            )
          }
        }
      }

      Text(
          text = state.selectionSummary,
          modifier = Modifier.fillMaxWidth(),
          style = MinecraftTheme.typography.muted,
      )
    }
  }
}
