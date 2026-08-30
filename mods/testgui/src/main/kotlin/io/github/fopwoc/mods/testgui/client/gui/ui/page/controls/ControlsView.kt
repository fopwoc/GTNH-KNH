package io.github.fopwoc.mods.testgui.client.gui.ui.page.controls

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.component.SegmentedControl
import io.github.fopwoc.mods.framework.ui.compose.component.Tabs
import io.github.fopwoc.mods.framework.ui.compose.component.ToggleButton
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.component.native.Checkbox
import io.github.fopwoc.mods.framework.ui.compose.component.native.Slider
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun ControlsView(
    state: ControlsModel,
    onSelectTab: (ControlsTab) -> Unit = {},
    onPresetSelected: (PowerPreset) -> Unit = {},
    onPowerLevelChanged: (Double) -> Unit = {},
    onAutomationChanged: (Boolean) -> Unit = {},
    onPrimaryAction: () -> Unit = {},
    onStyledAction: () -> Unit = {},
) {
  Tabs(
      options = ControlsTab.values().toList(),
      selected = state.activeTab,
      modifier = Modifier.fillMaxSize(),
      labelOf = ControlsTab::label,
      onSelected = onSelectTab,
  ) { tab ->
    when (tab) {
      ControlsTab.Actions ->
          ActionsTab(
              state = state,
              onPrimaryAction = onPrimaryAction,
              onStyledAction = onStyledAction,
              onAutomationChanged = onAutomationChanged,
          )
      ControlsTab.Selection ->
          SelectionTab(
              state = state,
              onPresetSelected = onPresetSelected,
              onPowerLevelChanged = onPowerLevelChanged,
              onAutomationChanged = onAutomationChanged,
          )
      ControlsTab.Feedback -> FeedbackTab(state = state)
    }
  }
}

@Composable
private fun ActionsTab(
    state: ControlsModel,
    onPrimaryAction: () -> Unit,
    onStyledAction: () -> Unit,
    onAutomationChanged: (Boolean) -> Unit,
) {
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = VerticalArrangement.spacedBy(8.uu),
  ) {
    Panel(modifier = Modifier.fillMaxWidth()) {
      Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = VerticalArrangement.spacedBy(6.uu),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
            verticalAlignment = VerticalAlignment.CENTER,
        ) {
          Button(
              text = "Native button",
              modifier =
                  Modifier.weight(1f).tooltip("Hosted Minecraft button with normal enabled state."),
              onClick = onPrimaryAction,
          )
          Button(
              text =
                  styledText {
                    withColor(MinecraftColor.Gold) { +"Styled" }
                    +" "
                    withColor(MinecraftColor.Aqua) { +"label" }
                  },
              modifier = Modifier.weight(1f).tooltip("Buttons accept StyledText labels too."),
              onClick = onStyledAction,
          )
        }
        ToggleButton(
            label = "Automation",
            checked = state.automationEnabled,
            modifier =
                Modifier.tooltip("Composite helper built on top of the native Button primitive."),
            onCheckedChange = onAutomationChanged,
        )
      }
    }

    FeedbackSummary(state = state)
  }
}

@Composable
private fun SelectionTab(
    state: ControlsModel,
    onPresetSelected: (PowerPreset) -> Unit,
    onPowerLevelChanged: (Double) -> Unit,
    onAutomationChanged: (Boolean) -> Unit,
) {
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = VerticalArrangement.spacedBy(8.uu),
  ) {
    Panel(modifier = Modifier.fillMaxWidth()) {
      Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = VerticalArrangement.spacedBy(6.uu),
      ) {
        Text(
            text = "Segmented control",
            style = TextStyle(color = Color.rgb(red = 0xFF, green = 0xD5, blue = 0x4A)),
        )
        SegmentedControl(
            options = PowerPreset.values().toList(),
            selected = state.preset,
            modifier = Modifier.fillMaxWidth(),
            labelOf = PowerPreset::label,
            onSelected = onPresetSelected,
        )
        Slider(
            value = state.powerLevel,
            onValueChange = onPowerLevelChanged,
            modifier =
                Modifier.fillMaxWidth()
                    .tooltip("Native hosted slider with formatted label and suffix."),
            valueRange = 0.0..100.0,
            label = "Power",
            suffix = "%",
            showDecimal = false,
        )
        Checkbox(
            label = "Allow automatic throttle management",
            checked = state.automationEnabled,
            modifier = Modifier.tooltip("Hosted checkbox with tooltip support."),
            onCheckedChange = onAutomationChanged,
        )
      }
    }

    FeedbackSummary(state = state)
  }
}

@Composable
private fun FeedbackTab(state: ControlsModel) {
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = VerticalArrangement.spacedBy(8.uu),
  ) {
    Panel(modifier = Modifier.fillMaxWidth()) {
      Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = VerticalArrangement.spacedBy(4.uu),
      ) {
        Text(
            text = "This screen intentionally layers composited helpers over raw hosted widgets:",
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(wrap = true),
        )
        Text(
            text =
                "• Button and styled button\n• ToggleButton helper\n• Checkbox\n• Slider\n• SegmentedControl\n• Tabs",
            modifier = Modifier.fillMaxWidth(),
            style =
                TextStyle(
                    color = Color.rgb(red = 0xB8, green = 0xD7, blue = 0xFF),
                    wrap = true,
                ),
        )
      }
    }

    FeedbackSummary(state = state)
  }
}

@Composable
private fun FeedbackSummary(state: ControlsModel) {
  Panel(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = VerticalArrangement.spacedBy(3.uu),
    ) {
      Text(
          text = "Runtime state",
          style = TextStyle(color = Color.rgb(red = 0xFF, green = 0xD5, blue = 0x4A)),
      )
      Text(text = "Preset: ${state.preset.label} · Power: ${state.powerLevel.toInt()}%")
      Text(text = "Automation: ${if (state.automationEnabled) "enabled" else "disabled"}")
      Text(text = "Primary clicks: ${state.primaryClicks} · Styled clicks: ${state.styledClicks}")
      Text(
          text = state.lastAction,
          modifier = Modifier.fillMaxWidth(),
          style =
              TextStyle(
                  color = Color.rgb(red = 0x8F, green = 0xD0, blue = 0xFF),
                  wrap = true,
              ),
      )
    }
  }
}
