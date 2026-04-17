package io.github.fopwoc.mods.gtnhclientworldbackup.client.gui

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.gtnhclientworldbackup.backup.ClientWorldBackupManager
import io.github.fopwoc.mods.gtnhclientworldbackup.client.command.BackupStatusCommand
import io.github.fopwoc.mods.gtnhclientworldbackup.client.highlight.BackedUpChunkHighlighter
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen

@SideOnly(Side.CLIENT)
class BackupStatusScreen : GuiScreen() {
    private companion object {
        const val CLOSE_BUTTON_ID = 0
        const val TOGGLE_HIGHLIGHTS_BUTTON_ID = 1
        const val CAPTURE_NOW_BUTTON_ID = 2
        const val CONTENT_WIDTH = 320
        const val PANEL_PADDING = 12
    }

    private var captureNowButton: GuiButton? = null
    private var toggleHighlightsButton: GuiButton? = null

    override fun initGui() {
        super.initGui()
        buttonList.clear()
        val buttonY = height - 30
        val left = width / 2 - 150

        captureNowButton = GuiButton(CAPTURE_NOW_BUTTON_ID, left, buttonY, 98, 20, "Capture now")
        toggleHighlightsButton = GuiButton(
            TOGGLE_HIGHLIGHTS_BUTTON_ID,
            left + 101,
            buttonY,
            98,
            20,
            toggleHighlightsLabel()
        )
        buttonList.add(captureNowButton)
        buttonList.add(toggleHighlightsButton)
        buttonList.add(GuiButton(CLOSE_BUTTON_ID, left + 202, buttonY, 98, 20, "Close"))
        updateButtons()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()
        updateButtons()

        val state = ClientWorldBackupManager.getUiState()
        val left = width / 2 - 150
        val top = 24
        val panelLeft = left - PANEL_PADDING
        val panelTop = top - PANEL_PADDING
        val panelRight = left + CONTENT_WIDTH + PANEL_PADDING
        val panelBottom = height - 40
        var lineY = top

        drawRect(panelLeft, panelTop, panelRight, panelBottom, 0xB0141418.toInt())
        drawHorizontalLine(panelLeft, panelRight - 1, panelTop, 0xFF4A4A56.toInt())
        drawHorizontalLine(panelLeft, panelRight - 1, panelBottom - 1, 0xFF4A4A56.toInt())
        drawVerticalLine(panelLeft, panelTop, panelBottom - 1, 0xFF4A4A56.toInt())
        drawVerticalLine(panelRight - 1, panelTop, panelBottom - 1, 0xFF4A4A56.toInt())

        drawCenteredString(fontRendererObj, "GTNH Observed World Backup", width / 2, lineY, 0xFFFFFF)
        lineY += 16
        drawCenteredString(fontRendererObj, state.statusLine, width / 2, lineY, 0x55FF55)
        lineY += 16

        drawSection(left, lineY, "Current session")
        lineY += 12
        drawLine(left, lineY, "Details", state.detailLine)
        lineY += 10
        drawLine(left, lineY, "Save", state.saveName ?: "No active session")
        lineY += 10
        drawLine(left, lineY, "Source", state.sourceName ?: "Waiting for world")
        lineY += 10
        drawLine(left, lineY, "Address", state.sourceAddress ?: "-")
        lineY += 10
        drawLine(left, lineY, "Dimension", state.currentDimensionId?.toString() ?: "-")
        lineY += 10
        drawLine(left, lineY, "Current dimension chunks", state.currentDimensionChunkCount.toString())
        lineY += 10
        drawLine(left, lineY, "Total unique chunks", state.totalUniqueChunks.toString())
        lineY += 10
        drawLine(left, lineY, "Next autosave", "${state.nextAutosaveSeconds}s")
        lineY += 10
        drawLine(left, lineY, "Highlights", if (state.highlightsEnabled) "Enabled" else "Disabled")
        lineY += 18

        drawSection(left, lineY, "Controls")
        lineY += 12
        lineY += drawWrapped(
            left,
            lineY,
            "Run /${BackupStatusCommand.COMMAND_NAME} in chat to open this GUI. Editing config/${io.github.fopwoc.mods.gtnhclientworldbackup.MOD_ID}.json is hot-reloaded while you play, and the buttons below still work for one-off actions.",
            CONTENT_WIDTH,
            0xCFCFCF
        ) + 6

        drawSection(left, lineY, "Commands")
        lineY += 12
        lineY += drawWrapped(
            left,
            lineY,
            "/${BackupStatusCommand.COMMAND_NAME}, /backupstatus, /observedbackup, /obbackup",
            CONTENT_WIDTH,
            0xE6E6E6
        ) + 6

        drawSection(left, lineY, "Actions")
        lineY += 12
        lineY += drawWrapped(
            left,
            lineY,
            "Capture now runs a save pass immediately for all currently loaded observed chunks. Highlights toggles the in-world saved chunk overlay.",
            CONTENT_WIDTH,
            0xCFCFCF
        ) + 6

        drawSection(left, lineY, "Highlight legend")
        lineY += 12
        state.highlightLegend.forEach { line ->
            lineY += drawWrapped(left, lineY, "• $line", CONTENT_WIDTH, 0xCFCFCF) + 4
        }

        lineY += 4
        drawSection(left, lineY, "Notes")
        lineY += 12
        state.notes.forEach { line ->
            lineY += drawWrapped(left, lineY, "• $line", CONTENT_WIDTH, 0xAFAFAF) + 4
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            CLOSE_BUTTON_ID -> mc.displayGuiScreen(null)
            CAPTURE_NOW_BUTTON_ID -> ClientWorldBackupManager.captureNowFromUi()
            TOGGLE_HIGHLIGHTS_BUTTON_ID -> {
                BackedUpChunkHighlighter.toggleHighlights()
            }
        }
        updateButtons()
    }

    override fun doesGuiPauseGame(): Boolean = false

    private fun drawSection(x: Int, y: Int, title: String) {
        fontRendererObj.drawStringWithShadow(title, x, y, 0xFFD54A)
    }

    private fun drawLine(x: Int, y: Int, label: String, value: String) {
        fontRendererObj.drawStringWithShadow("$label: $value", x, y, 0xE6E6E6)
    }

    private fun drawWrapped(x: Int, y: Int, text: String, width: Int, color: Int): Int {
        fontRendererObj.drawSplitString(text, x, y, width, color)
        return fontRendererObj.splitStringWidth(text, width)
    }

    private fun toggleHighlightsLabel(): String {
        return if (BackedUpChunkHighlighter.areHighlightsEnabled()) {
            "Highlights: ON"
        } else {
            "Highlights: OFF"
        }
    }

    private fun updateButtons() {
        captureNowButton?.enabled = ClientWorldBackupManager.canCaptureNow()
        toggleHighlightsButton?.displayString = toggleHighlightsLabel()
    }
}


