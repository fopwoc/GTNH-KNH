package io.github.fopwoc.mods.testgui.client.gui.ui.page.scrollclipstress

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.component.native.Checkbox
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
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
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.AccentText
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.BodyText
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.SectionBlock
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.TestGuiPalette

@Composable
fun ScrollClipStressView(
    state: ScrollClipStressModel,
    onAddLane: () -> Unit = {},
    onRemoveLane: () -> Unit = {},
    onIncreaseBadgeOffset: () -> Unit = {},
    onDecreaseBadgeOffset: () -> Unit = {},
    onCompactModeChange: (Boolean) -> Unit = {},
    onAlternatingBadgesChange: (Boolean) -> Unit = {},
) {
  val scrollState = rememberScrollState()
  val laneHeight = if (state.compactMode) 18 else 26

  Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = VerticalArrangement.spacedBy(8.uu),
  ) {
    SectionBlock(title = "Stress controls", modifier = Modifier.fillMaxWidth()) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
          verticalAlignment = VerticalAlignment.CENTER,
      ) {
        Button(text = "+ lane", modifier = Modifier.weight(1f), onClick = onAddLane)
        Button(text = "- lane", modifier = Modifier.weight(1f), onClick = onRemoveLane)
        Button(
            text = "+ badge offset",
            modifier = Modifier.weight(1f),
            onClick = onIncreaseBadgeOffset,
        )
        Button(
            text = "- badge offset",
            modifier = Modifier.weight(1f),
            onClick = onDecreaseBadgeOffset,
        )
      }
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.spacedBy(8.uu),
          verticalAlignment = VerticalAlignment.CENTER,
      ) {
        Checkbox(
            label = "Compact lanes",
            checked = state.compactMode,
            onCheckedChange = onCompactModeChange,
        )
        Checkbox(
            label = "Alternating badges",
            checked = state.alternatingBadges,
            onCheckedChange = onAlternatingBadgesChange,
        )
      }
      AccentText("Lanes: ${state.laneCount} · Badge offset: ${state.badgeOffset}px")
    }

    SectionBlock(
        title = "Scrollable clip pressure",
        modifier = Modifier.weight(1f).fillMaxWidth(),
        elevated = true,
    ) {
      Column(
          modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
          verticalArrangement = VerticalArrangement.spacedBy(4.uu),
      ) {
        repeat(state.laneCount) { index ->
          val badgeLeft =
              if (state.alternatingBadges && index % 2 == 1) state.badgeOffset / 2
              else state.badgeOffset
          val rowColor = if (index % 2 == 0) Color(0x60353E49) else Color(0x60404856)
          Box(
              modifier =
                  Modifier.fillMaxWidth()
                      .height(laneHeight.uu)
                      .background(rowColor)
                      .border(TestGuiPalette.SurfaceBorder)
          ) {
            Text(
                text = "Lane ${index + 1}",
                modifier = Modifier.align(Alignment.CenterStart).offset(x = 8.uu),
                style = TextStyle(color = TestGuiPalette.Foreground),
            )
            Box(
                modifier =
                    Modifier.size(width = 68.uu, height = (laneHeight + 8).uu)
                        .background(Color(0x907088C8))
                        .border(TestGuiPalette.ShellBorder)
                        .align(Alignment.CenterEnd)
                        .offset(x = badgeLeft.uu)
            ) {
              Text(
                  text = "badge ${index + 1}",
                  modifier = Modifier.align(Alignment.Center),
                  style =
                      TextStyle(
                          color = TestGuiPalette.Foreground,
                          alignment = HorizontalAlignment.CENTER,
                          wrap = true,
                      ),
              )
            }
          }
        }
      }
    }

    BodyText(
        "Use this route to inspect whether long scrolled regions stay stable while partially offset badges pressure clipping and hit testing."
    )
    AccentText("Scroll: ${scrollState.value} / ${scrollState.maxValue}")
  }
}
