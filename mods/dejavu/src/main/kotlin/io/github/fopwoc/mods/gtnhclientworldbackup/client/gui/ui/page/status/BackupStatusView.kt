package io.github.fopwoc.mods.gtnhclientworldbackup.client.gui.ui.page.status

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Spacer
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
import io.github.fopwoc.mods.gtnhclientworldbackup.MOD_ID
import io.github.fopwoc.mods.gtnhclientworldbackup.client.command.BackupStatusCommand

private const val CONTENT_WIDTH = 320
private const val PANEL_PADDING = 12

@Composable
fun BackupStatusView(
    model: BackupStatusModel,
    screenHeight: Int,
    onCaptureNow: () -> Unit,
    onToggleHighlights: () -> Unit,
    onClose: () -> Unit,
) {
  val scrollState = rememberScrollState()
  val panelWidth = (CONTENT_WIDTH + PANEL_PADDING * 2).uu
  val panelHeight = (screenHeight - 48).coerceAtLeast(180).uu

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier =
            Modifier.width(panelWidth)
                .height(panelHeight)
                .background(Color(0xB0141418))
                .border(Color(0xFF4A4A56))
                .padding(PANEL_PADDING.uu)
                .align(Alignment.Center),
        verticalArrangement = VerticalArrangement.spacedBy(8.uu),
    ) {
      Column(
          modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState),
          verticalArrangement = VerticalArrangement.spacedBy(6.uu),
      ) {
        Text(
            text = "GTNH Observed World Backup",
            modifier = Modifier.fillMaxWidth(),
            style =
                TextStyle(
                    color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                    alignment = HorizontalAlignment.CENTER,
                ),
        )
        Text(
            text = model.statusLine,
            modifier = Modifier.fillMaxWidth(),
            style =
                TextStyle(
                    color = Color.rgb(red = 0x55, green = 0xFF, blue = 0x55),
                    alignment = HorizontalAlignment.CENTER,
                    wrap = true,
                ),
        )
        SectionHeading("Current session")
        DetailLine("Details", model.detailLine)
        DetailLine("Save", model.saveName ?: "No active session")
        DetailLine("Source", model.sourceName ?: "Waiting for world")
        DetailLine("Address", model.sourceAddress ?: "-")
        DetailLine("Dimension", model.currentDimensionId?.toString() ?: "-")
        DetailLine("Current dimension chunks", model.currentDimensionChunkCount.toString())
        DetailLine("Total unique chunks", model.totalUniqueChunks.toString())
        DetailLine("Next autosave", "${model.nextAutosaveSeconds}s")
        DetailLine("Highlights", if (model.highlightsEnabled) "Enabled" else "Disabled")

        SectionHeading("Controls")
        BodyText(
            "Run /${BackupStatusCommand.COMMAND_NAME} in chat to open this GUI. Editing config/$MOD_ID.json is hot-reloaded while you play, and the buttons below still work for one-off actions."
        )

        SectionHeading("Commands")
        EmphasizedText(
            "/${BackupStatusCommand.COMMAND_NAME}, /backupstatus, /observedbackup, /obbackup"
        )

        SectionHeading("Actions")
        BodyText(
            "Capture now runs a save pass immediately for all currently loaded observed chunks. Highlights toggles the in-world saved chunk overlay."
        )

        SectionHeading("Highlight legend")
        model.highlightLegend.forEach { line ->
          BulletText(line)
        }

        SectionHeading("Notes")
        model.notes.forEach { line ->
          MutedBulletText(line)
        }
      }

      Spacer(height = 2.uu)

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
          verticalAlignment = VerticalAlignment.CENTER,
      ) {
        Button(
            text = "Capture now",
            modifier = Modifier.weight(1f),
            enabled = model.captureNowEnabled,
            onClick = onCaptureNow,
        )
        Button(
            text = if (model.highlightsEnabled) "Highlights: ON" else "Highlights: OFF",
            modifier = Modifier.weight(1f),
            onClick = onToggleHighlights,
        )
        Button(
            text = "Close",
            modifier = Modifier.weight(1f),
            onClick = onClose,
        )
      }
    }
  }
}

@Composable
private fun SectionHeading(text: String) {
  Text(
      text = text,
      modifier = Modifier.fillMaxWidth(),
      style = TextStyle(color = Color.rgb(red = 0xFF, green = 0xD5, blue = 0x4A)),
  )
}

@Composable
private fun DetailLine(label: String, value: String) {
  BodyText("$label: $value")
}

@Composable
private fun BodyText(text: String) {
  Text(
      text = text,
      modifier = Modifier.fillMaxWidth(),
      style =
          TextStyle(
              color = Color.rgb(red = 0xCF, green = 0xCF, blue = 0xCF),
              wrap = true,
          ),
  )
}

@Composable
private fun EmphasizedText(text: String) {
  Text(
      text = text,
      modifier = Modifier.fillMaxWidth(),
      style =
          TextStyle(
              color = Color.rgb(red = 0xE6, green = 0xE6, blue = 0xE6),
              wrap = true,
          ),
  )
}

@Composable
private fun BulletText(text: String) {
  BodyText("• $text")
}

@Composable
private fun MutedBulletText(text: String) {
  Text(
      text = "• $text",
      modifier = Modifier.fillMaxWidth(),
      style =
          TextStyle(
              color = Color.rgb(red = 0xAF, green = 0xAF, blue = 0xAF),
              wrap = true,
          ),
  )
}
