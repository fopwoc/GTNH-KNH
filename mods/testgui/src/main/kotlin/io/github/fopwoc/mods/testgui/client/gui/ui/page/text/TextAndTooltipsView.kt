package io.github.fopwoc.mods.testgui.client.gui.ui.page.text

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.component.Tabs
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun TextAndTooltipsView(
    state: TextAndTooltipsModel,
    onSelectTab: (TextDemoTab) -> Unit = {},
    onCycleAccentPreview: () -> Unit = {},
) {
  Tabs(
      options = TextDemoTab.values().toList(),
      selected = state.activeTab,
      modifier = Modifier.fillMaxSize(),
      labelOf = TextDemoTab::label,
      onSelected = onSelectTab,
  ) { tab ->
    when (tab) {
      TextDemoTab.Wrapped -> WrappedTextTab()
      TextDemoTab.Styled ->
          StyledTextTab(
              accentPasses = state.accentPasses,
              onCycleAccentPreview = onCycleAccentPreview,
          )
      TextDemoTab.Tooltips -> TooltipsTab()
    }
  }
}

@Composable
private fun WrappedTextTab() {
  Panel(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = VerticalArrangement.spacedBy(6.uu),
    ) {
      Text(
          text =
              "The Text node supports alignment, color, wrap and shadow. This paragraph is intentionally long enough to wrap across multiple lines so the layout engine can be checked in a realistic panel width.",
          modifier = Modifier.fillMaxWidth(),
          style = TextStyle(wrap = true),
      )
      Text(
          text = "Secondary copy can be tinted independently to verify style separation.",
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

@Composable
private fun StyledTextTab(
    accentPasses: Int,
    onCycleAccentPreview: () -> Unit,
) {
  val styledLine = styledText {
    withColor(MinecraftColor.Gold) { +"Minecraft " }
    withBold {
      withColor(MinecraftColor.Yellow) { +"styled " }
    }
    withColor(MinecraftColor.Aqua) { +"text" }
    +" "
    withItalic {
      withColor(MinecraftColor.LightPurple) { +"spans" }
    }
  }

  Panel(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = VerticalArrangement.spacedBy(6.uu),
    ) {
      Text(
          text = styledLine,
          modifier = Modifier.fillMaxWidth(),
      )
      Button(
          text = styledAccentLabel(accentPasses),
          modifier =
              Modifier.tooltip(
                  styledText {
                    withColor(MinecraftColor.Green) { +"StyledText" }
                    +" tooltips can mix formatting too."
                  }
              ),
          onClick = onCycleAccentPreview,
      )
      Text(
          text = "Accent preview clicks: $accentPasses",
          style = TextStyle(color = Color.rgb(red = 0xB8, green = 0xD7, blue = 0xFF)),
      )
    }
  }
}

@Composable
private fun TooltipsTab() {
  Panel(
      modifier =
          Modifier.fillMaxWidth()
              .tooltip(
                  StyledText.of(
                      "Hover anywhere inside this panel to validate container-level tooltips."
                  )
              )
  ) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = VerticalArrangement.spacedBy(6.uu),
    ) {
      Text(
          text =
              "Hover the panel, then hover the button below to make sure the topmost target wins.",
          modifier = Modifier.fillMaxWidth(),
          style = TextStyle(wrap = true),
      )
      Button(
          text = "Tooltip target",
          modifier =
              Modifier.tooltip(
                  styledText {
                    withColor(MinecraftColor.Gold) { +"Button tooltip" }
                    +" overrides the parent panel while hovered."
                  }
              ),
          onClick = {},
      )
    }
  }
}

private fun styledAccentLabel(accentPasses: Int): StyledText {
  val cycleLabel =
      when (accentPasses % 3) {
        1 -> MinecraftColor.Aqua to "Aqua"
        2 -> MinecraftColor.LightPurple to "Magenta"
        else -> MinecraftColor.Green to "Green"
      }
  return styledText {
    +"Cycle accent: "
    withColor(cycleLabel.first) { +cycleLabel.second }
  }
}
