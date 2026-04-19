package io.github.fopwoc.mods.gtnhclientworldbackup.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Spacer
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeBackgroundStyle
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreen
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberScrollState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.gtnhclientworldbackup.MOD_ID
import io.github.fopwoc.mods.gtnhclientworldbackup.backup.ClientWorldBackupManager
import io.github.fopwoc.mods.gtnhclientworldbackup.client.command.BackupStatusCommand
import io.github.fopwoc.mods.gtnhclientworldbackup.client.highlight.BackedUpChunkHighlighter

@SideOnly(Side.CLIENT)
class BackupStatusScreen : ComposeGuiScreen() {
    private companion object {
        const val CONTENT_WIDTH = 320
        const val PANEL_PADDING = 12
    }

    private var uiState by mutableStateOf(ClientWorldBackupManager.getUiState())
    private var captureNowEnabled by mutableStateOf(ClientWorldBackupManager.canCaptureNow())

    override val composeBackgroundStyle: ComposeBackgroundStyle = ComposeBackgroundStyle.VanillaDefault

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        refreshUiState()
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun doesGuiPauseGame(): Boolean = false

    @Composable
    override fun Content() {
        val scrollState = rememberScrollState()
        val panelWidth = (CONTENT_WIDTH + PANEL_PADDING * 2).uu
        val panelHeight = (height - 48).coerceAtLeast(180).uu

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(panelWidth)
                    .height(panelHeight)
                    .background(Color(0xB0141418))
                    .border(Color(0xFF4A4A56))
                    .padding(PANEL_PADDING.uu)
                    .align(Alignment.Center),
                verticalArrangement = VerticalArrangement.spacedBy(8.uu)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = VerticalArrangement.spacedBy(6.uu)
                ) {
                    Text(
                        text = "GTNH Observed World Backup",
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(
                            color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                            alignment = HorizontalAlignment.CENTER
                        )
                    )
                    Text(
                        text = uiState.statusLine,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(
                            color = Color.rgb(red = 0x55, green = 0xFF, blue = 0x55),
                            alignment = HorizontalAlignment.CENTER,
                            wrap = true
                        )
                    )
                    SectionHeading("Current session")
                    DetailLine("Details", uiState.detailLine)
                    DetailLine("Save", uiState.saveName ?: "No active session")
                    DetailLine("Source", uiState.sourceName ?: "Waiting for world")
                    DetailLine("Address", uiState.sourceAddress ?: "-")
                    DetailLine("Dimension", uiState.currentDimensionId?.toString() ?: "-")
                    DetailLine("Current dimension chunks", uiState.currentDimensionChunkCount.toString())
                    DetailLine("Total unique chunks", uiState.totalUniqueChunks.toString())
                    DetailLine("Next autosave", "${uiState.nextAutosaveSeconds}s")
                    DetailLine("Highlights", if (uiState.highlightsEnabled) "Enabled" else "Disabled")

                    SectionHeading("Controls")
                    BodyText(
                        "Run /${BackupStatusCommand.COMMAND_NAME} in chat to open this GUI. Editing config/$MOD_ID.json is hot-reloaded while you play, and the buttons below still work for one-off actions."
                    )

                    SectionHeading("Commands")
                    EmphasizedText("/${BackupStatusCommand.COMMAND_NAME}, /backupstatus, /observedbackup, /obbackup")

                    SectionHeading("Actions")
                    BodyText(
                        "Capture now runs a save pass immediately for all currently loaded observed chunks. Highlights toggles the in-world saved chunk overlay."
                    )

                    SectionHeading("Highlight legend")
                    uiState.highlightLegend.forEach { line ->
                        BulletText(line)
                    }

                    SectionHeading("Notes")
                    uiState.notes.forEach { line ->
                        MutedBulletText(line)
                    }
                }

                Spacer(height = 2.uu)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
                    verticalAlignment = VerticalAlignment.CENTER
                ) {
                    Button(
                        text = "Capture now",
                        modifier = Modifier.weight(1f),
                        enabled = captureNowEnabled,
                        onClick = {
                            ClientWorldBackupManager.captureNowFromUi()
                            refreshUiState()
                        }
                    )
                    Button(
                        text = if (uiState.highlightsEnabled) "Highlights: ON" else "Highlights: OFF",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            BackedUpChunkHighlighter.toggleHighlights()
                            refreshUiState()
                        }
                    )
                    Button(
                        text = "Close",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            mc.displayGuiScreen(null)
                        }
                    )
                }
            }
        }
    }

    private fun refreshUiState() {
        uiState = ClientWorldBackupManager.getUiState()
        captureNowEnabled = ClientWorldBackupManager.canCaptureNow()
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = TextStyle(color = Color.rgb(red = 0xFF, green = 0xD5, blue = 0x4A))
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
        style = TextStyle(
            color = Color.rgb(red = 0xCF, green = 0xCF, blue = 0xCF),
            wrap = true
        )
    )
}

@Composable
private fun EmphasizedText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = TextStyle(
            color = Color.rgb(red = 0xE6, green = 0xE6, blue = 0xE6),
            wrap = true
        )
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
        style = TextStyle(
            color = Color.rgb(red = 0xAF, green = 0xAF, blue = 0xAF),
            wrap = true
        )
    )
}
