package io.github.fopwoc.mods.testgui.client.gui.ui.page.hostedstress

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.component.native.Checkbox
import io.github.fopwoc.mods.framework.ui.compose.component.native.SelectableList
import io.github.fopwoc.mods.framework.ui.compose.component.native.TextField
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.AccentText
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.BodyText
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.SectionBlock
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.sectionTitleTextStyle

@Composable
fun HostedStressView(
    state: HostedStressModel,
    onSelectedIndexChange: (Int) -> Unit = {},
    onFocusField: (Int) -> Unit = {},
    onCycleFocus: () -> Unit = {},
    onLoadSelection: () -> Unit = {},
    onMirrorSelection: () -> Unit = {},
    onMirrorEnabledChange: (Boolean) -> Unit = {},
    onCommitSnapshot: () -> Unit = {},
) {
  Row(
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = HorizontalArrangement.spacedBy(8.uu),
      verticalAlignment = VerticalAlignment.TOP,
  ) {
    SectionBlock(
        title = "Stress inputs",
        modifier = Modifier.width(180.uu).fillMaxHeight(),
        elevated = true,
    ) {
      BodyText("Swap list selections and keep redirecting focus to different text fields.")
      SelectableList(
          items = state.items,
          selectedIndex = state.selectedIndex,
          modifier = Modifier.fillMaxWidth(),
          onSelectedIndexChange = onSelectedIndexChange,
      )
      AccentText("Focused field: ${state.focusedIndex + 1}")
    }

    Column(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        verticalArrangement = VerticalArrangement.spacedBy(8.uu),
    ) {
      SectionBlock(title = "Hosted TextField churn", modifier = Modifier.fillMaxWidth()) {
        state.fields.forEachIndexed { index, fieldState ->
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
              verticalAlignment = VerticalAlignment.CENTER,
          ) {
            Text(
                text = "F${index + 1}",
                style = sectionTitleTextStyle(),
            )
            TextField(
                state = fieldState,
                modifier = Modifier.weight(1f),
                placeholder = "Field ${index + 1}",
            )
            Button(
                text = if (state.focusedIndex == index) "Active" else "Focus",
                enabled = state.focusedIndex != index,
                onClick = {
                  onFocusField(index)
                },
            )
          }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
            verticalAlignment = VerticalAlignment.CENTER,
        ) {
          Button(
              text = "Cycle focus",
              modifier = Modifier.weight(1f),
              onClick = onCycleFocus,
          )
          Button(
              text = "Load into focused",
              modifier = Modifier.weight(1f),
              onClick = onLoadSelection,
          )
          Button(
              text = "Commit snapshot",
              modifier = Modifier.weight(1f),
              onClick = onCommitSnapshot,
          )
        }
      }

      SectionBlock(title = "Batch mutation", modifier = Modifier.fillMaxWidth()) {
        Checkbox(
            label = "Mirror selected entry into every field",
            checked = state.mirrorEnabled,
            onCheckedChange = onMirrorEnabledChange,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
            verticalAlignment = VerticalAlignment.CENTER,
        ) {
          Button(
              text = "Mirror selection now",
              modifier = Modifier.weight(1f),
              enabled = state.mirrorEnabled,
              onClick = onMirrorSelection,
          )
          Button(
              text = "Load focused only",
              modifier = Modifier.weight(1f),
              onClick = onLoadSelection,
          )
        }
        BodyText(
            "This route intentionally mutates several hosted states quickly to shake out focus handoff and widget reuse bugs."
        )
        AccentText("Commits: ${state.commits}")
        BodyText("Last snapshot: ${state.lastSnapshot}")
      }
    }
  }
}
