package io.github.fopwoc.mods.testgui.client.gui.ui.page.layout

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.component.SegmentedControl
import io.github.fopwoc.mods.framework.ui.compose.component.Tabs
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.ColumnScope
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.RowScope
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberScrollState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun LayoutView(
    state: LayoutModel,
    onSelectTab: (LayoutTab) -> Unit = {},
    onAlignmentSelected: (BoxAlignmentPreset) -> Unit = {},
    onAddScrollChip: () -> Unit = {},
    onRemoveScrollChip: () -> Unit = {},
) {
  Tabs(
      options = LayoutTab.values().toList(),
      selected = state.activeTab,
      modifier = Modifier.fillMaxSize(),
      labelOf = LayoutTab::label,
      onSelected = onSelectTab,
  ) { tab ->
    when (tab) {
      LayoutTab.Box -> BoxTab(state = state, onAlignmentSelected = onAlignmentSelected)
      LayoutTab.Weight -> WeightTab()
      LayoutTab.Scroll ->
          ScrollTab(
              state = state,
              onAddScrollChip = onAddScrollChip,
              onRemoveScrollChip = onRemoveScrollChip,
          )
    }
  }
}

@Composable
private fun BoxTab(
    state: LayoutModel,
    onAlignmentSelected: (BoxAlignmentPreset) -> Unit,
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
        SegmentedControl(
            options = BoxAlignmentPreset.values().toList(),
            selected = state.alignmentPreset,
            modifier = Modifier.fillMaxWidth(),
            labelOf = BoxAlignmentPreset::label,
            onSelected = onAlignmentSelected,
        )
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(148.uu)
                    .background(Color(0x502A2F3A))
                    .border(Color(0xFF4A4A56))
        ) {
          Box(
              modifier =
                  Modifier.size(64.uu)
                      .background(Color(0x905584B8))
                      .align(state.alignmentPreset.alignment)
          ) {
            Text(
                text = state.alignmentPreset.label,
                modifier = Modifier.align(Alignment.Center),
                style =
                    TextStyle(
                        color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                        alignment = HorizontalAlignment.CENTER,
                        wrap = true,
                    ),
            )
          }
          Text(
              text = "Offset badge",
              modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).uu, y = 10.uu),
              style = TextStyle(color = Color.rgb(red = 0xFF, green = 0xD5, blue = 0x4A)),
          )
        }
      }
    }
  }
}

@Composable
private fun WeightTab() {
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
            text = "Row weights",
            style = TextStyle(color = Color.rgb(red = 0xFF, green = 0xD5, blue = 0x4A)),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
            verticalAlignment = VerticalAlignment.CENTER,
        ) {
          WeightCell(label = "1x", color = Color(0x90536B8C), weight = 1f)
          WeightCell(label = "2x", color = Color(0x906F8C53), weight = 2f)
          WeightCell(label = "1x", color = Color(0x908C5353), weight = 1f)
        }
      }
    }

    Panel(modifier = Modifier.fillMaxWidth()) {
      Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = VerticalArrangement.spacedBy(4.uu),
      ) {
        Text(
            text = "Column weights",
            style = TextStyle(color = Color.rgb(red = 0xFF, green = 0xD5, blue = 0x4A)),
        )
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .height(132.uu)
                    .background(Color(0x502A2F3A))
                    .border(Color(0xFF4A4A56)),
            verticalArrangement = VerticalArrangement.spacedBy(4.uu),
        ) {
          WeightColumnCell(label = "Header", color = Color(0x90798CC4), weight = 1f)
          WeightColumnCell(label = "Weighted body", color = Color(0x90568C72), weight = 2f)
          WeightColumnCell(label = "Footer", color = Color(0x908C6D56), weight = 1f)
        }
      }
    }
  }
}

@Composable
private fun ScrollTab(
    state: LayoutModel,
    onAddScrollChip: () -> Unit,
    onRemoveScrollChip: () -> Unit,
) {
  val scrollState = rememberScrollState()

  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = VerticalArrangement.spacedBy(8.uu),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
        verticalAlignment = VerticalAlignment.CENTER,
    ) {
      Button(
          text = "Add chip",
          modifier = Modifier.weight(1f),
          onClick = onAddScrollChip,
      )
      Button(
          text = "Remove chip",
          modifier = Modifier.weight(1f),
          onClick = onRemoveScrollChip,
      )
    }
    Panel(modifier = Modifier.fillMaxWidth()) {
      Column(
          modifier = Modifier.fillMaxWidth().height(170.uu).verticalScroll(scrollState),
          verticalArrangement = VerticalArrangement.spacedBy(4.uu),
      ) {
        repeat(state.scrollChipCount) { index ->
          Box(
              modifier =
                  Modifier.fillMaxWidth()
                      .height(20.uu)
                      .background(if (index % 2 == 0) Color(0x60414A56) else Color(0x60333742))
                      .border(Color(0xFF4A4A56))
          ) {
            Text(
                text = "Scroll chip ${index + 1}",
                modifier = Modifier.align(Alignment.CenterStart).offset(x = 6.uu),
                style = TextStyle(color = Color.rgb(red = 0xE6, green = 0xE6, blue = 0xE6)),
            )
          }
        }
      }
    }
    Text(
        text = "Scroll offset: ${scrollState.value} / ${scrollState.maxValue}",
        modifier = Modifier.fillMaxWidth(),
        style =
            TextStyle(
                color = Color.rgb(red = 0x8F, green = 0xD0, blue = 0xFF),
                wrap = true,
            ),
    )
  }
}

@Composable
private fun RowScope.WeightCell(
    label: String,
    color: Color,
    weight: Float,
) {
  Box(
      modifier = Modifier.weight(weight).height(46.uu).background(color).border(Color(0xFF4A4A56))
  ) {
    Text(
        text = label,
        modifier = Modifier.align(Alignment.Center),
        style = TextStyle(alignment = HorizontalAlignment.CENTER),
    )
  }
}

@Composable
private fun ColumnScope.WeightColumnCell(
    label: String,
    color: Color,
    weight: Float,
) {
  Box(
      modifier = Modifier.weight(weight).fillMaxWidth().background(color).border(Color(0xFF4A4A56))
  ) {
    Text(
        text = label,
        modifier = Modifier.align(Alignment.Center),
        style = TextStyle(alignment = HorizontalAlignment.CENTER),
    )
  }
}
