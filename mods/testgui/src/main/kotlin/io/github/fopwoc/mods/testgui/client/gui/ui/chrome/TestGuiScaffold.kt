package io.github.fopwoc.mods.testgui.client.gui.ui.chrome

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.testgui.client.gui.ui.TestGuiDestination

@Composable
fun TestGuiScaffold(
    screenWidth: Int,
    screenHeight: Int,
    currentDestination: TestGuiDestination,
    canNavigateBack: Boolean,
    debugStatus: String,
    onNavigateBack: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
  val panelWidth = (screenWidth - 28).coerceAtLeast(320).uu
  val panelHeight = (screenHeight - 28).coerceAtLeast(220).uu

  Box(modifier = Modifier.fillMaxSize()) {
    Panel(
        modifier = Modifier.width(panelWidth).height(panelHeight).align(Alignment.Center),
        backgroundColor = TestGuiPalette.ShellBackground,
        borderColor = TestGuiPalette.ShellBorder,
    ) {
      Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = VerticalArrangement.spacedBy(TestGuiChromeDefaults.SectionGap),
      ) {
        TestGuiCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
          io.github.fopwoc.mods.framework.ui.compose.foundation.Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
              verticalAlignment = VerticalAlignment.CENTER,
          ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = VerticalArrangement.spacedBy(2.uu),
            ) {
              Text(
                  text = currentDestination.title,
                  modifier = Modifier.fillMaxWidth(),
                  style = titleTextStyle(),
              )
              MutedText(
                  text = currentDestination.summary,
                  modifier = Modifier.fillMaxWidth(),
                  wrap = true,
              )
            }
            if (canNavigateBack) {
              Button(
                  text = "Back",
                  onClick = onNavigateBack,
              )
            }
            Button(
                text = "Close",
                onClick = onClose,
            )
          }
        }

        TestGuiCard(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            elevated = true,
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            content()
          }
        }

        MutedText(
            text =
                if (canNavigateBack) {
                  "This screen is full-screen within the demo app. Use Back to return to the menu list. $debugStatus"
                } else {
                  "Choose a screen from the menu list to open it full-screen. $debugStatus"
                },
            modifier = Modifier.fillMaxWidth(),
            alignment = HorizontalAlignment.CENTER,
            wrap = true,
        )
      }
    }
  }
}
