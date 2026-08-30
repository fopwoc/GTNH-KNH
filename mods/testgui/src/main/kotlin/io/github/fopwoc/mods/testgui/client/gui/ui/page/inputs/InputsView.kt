package io.github.fopwoc.mods.testgui.client.gui.ui.page.inputs

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.component.native.SelectableList
import io.github.fopwoc.mods.framework.ui.compose.component.native.TextField
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.AccentText
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.BodyText
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.SectionBlock

@Composable
fun InputsView(
    state: InputsModel,
    onSelectedIndexChange: (Int) -> Unit = {},
    onLoadSelection: () -> Unit = {},
    onCommitDraft: () -> Unit = {},
    onRequestFocus: () -> Unit = {},
    onClearFocus: () -> Unit = {},
    onResetDraft: () -> Unit = {},
) {
  Row(
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = HorizontalArrangement.spacedBy(8.uu),
      verticalAlignment = VerticalAlignment.TOP,
  ) {
    SectionBlock(
        title = "SelectableList",
        modifier = Modifier.width(164.uu).fillMaxHeight(),
    ) {
      Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = VerticalArrangement.spacedBy(6.uu),
      ) {
        SelectableList(
            items = state.items,
            selectedIndex = state.selectedIndex,
            modifier = Modifier.fillMaxWidth(),
            onSelectedIndexChange = onSelectedIndexChange,
        )
        BodyText(
            text = "Selected: ${state.items[state.selectedIndex]}",
            modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    Column(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        verticalArrangement = VerticalArrangement.spacedBy(8.uu),
    ) {
      SectionBlock(title = "Hosted TextField", modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = VerticalArrangement.spacedBy(6.uu),
        ) {
          TextField(
              state = state.fieldState,
              modifier =
                  Modifier.fillMaxWidth()
                      .tooltip(
                          "Hosted text field keeps focus state and mutates its TextFieldState directly."
                      ),
              placeholder = "Select an item or type your own draft",
          )
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
              verticalAlignment = VerticalAlignment.CENTER,
          ) {
            Button(
                text = "Load selection",
                modifier = Modifier.weight(1f),
                onClick = onLoadSelection,
            )
            Button(
                text = "Commit",
                modifier = Modifier.weight(1f),
                onClick = onCommitDraft,
            )
            Button(
                text = "Reset",
                modifier = Modifier.weight(1f),
                onClick = onResetDraft,
            )
          }
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
              verticalAlignment = VerticalAlignment.CENTER,
          ) {
            Button(
                text = "Focus",
                modifier = Modifier.weight(1f),
                onClick = onRequestFocus,
            )
            Button(
                text = "Blur",
                modifier = Modifier.weight(1f),
                onClick = onClearFocus,
            )
          }
        }
      }

      SectionBlock(title = "Bound state", modifier = Modifier.fillMaxWidth(), elevated = true) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = VerticalArrangement.spacedBy(4.uu),
        ) {
          BodyText(text = "Draft: ${state.fieldState.text.ifEmpty { "<empty>" }}")
          BodyText(text = "Focused: ${state.fieldState.focused}")
          BodyText(text = "Loads: ${state.loadCount} · Commits: ${state.commitCount}")
          AccentText(
              text = "Last committed: ${state.lastCommittedText}",
              modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}
