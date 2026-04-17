package io.github.fopwoc.mods.gtnhmeasurement.client.gui

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementSelectionState
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementOverlayPalette
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementShortcutScheme
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.OverlayVisualState
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen

@SideOnly(Side.CLIENT)
class MeasurementModeScreen : GuiScreen() {
    private val selectableModes = listOf(
        MeasurementMode.LINE,
        MeasurementMode.AREA,
        MeasurementMode.DISABLED
    )

    override fun initGui() {
        buttonList.clear()

        val centerX = width / 2
        val centerY = height / 2 - 12
        selectableModes.forEachIndexed { index, mode ->
            buttonList.add(
                GuiButton(
                    index,
                    centerX - 100,
                    centerY - 18 + index * 24,
                    200,
                    20,
                    buttonLabel(mode)
                )
            )
        }
        buttonList.add(
            GuiButton(
                99,
                centerX - 100,
                centerY + 60,
                200,
                20,
                "Close"
            )
        )
    }

    override fun actionPerformed(button: GuiButton) {
        val selectedMode = selectableModes.getOrNull(button.id)
        when {
            selectedMode != null -> {
                if (selectedMode.isEnabled) {
                    MeasurementSession.switchTo(selectedMode)
                } else {
                    MeasurementSession.disable()
                    MeasurementSelectionState.clearTransientState()
                }
            }
            button.id == 99 -> mc.displayGuiScreen(null)
        }

        if (button.id != 99) {
            initGui()
        }
    }

    override fun doesGuiPauseGame(): Boolean = false

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawRect(0, 0, width, height, 0xA0101010.toInt())
        val font = fontRendererObj ?: run {
            super.drawScreen(mouseX, mouseY, partialTicks)
            return
        }

        drawCenteredString(font, "Measurement Mode", width / 2, height / 2 - 55, 0xFFFFFF)
        drawCenteredString(
            font,
            "Current: ${MeasurementSession.mode.displayName}",
            width / 2,
            height / 2 - 42,
            if (MeasurementSession.isActive) {
                MeasurementOverlayPalette.style(MeasurementSession.mode, OverlayVisualState.NORMAL)
                    .shapeColor(MeasurementSession.mode)
            } else {
                0xFFAAAA
            }
        )
        val footerText = if (MeasurementSession.isActive) {
            MeasurementShortcutScheme.footerText()
        } else {
            "Select a mode to enable measuring"
        }
        drawCenteredString(font, footerText, width / 2, height - 24, 0xB8B8B8)
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun buttonLabel(mode: MeasurementMode): String {
        val selected = MeasurementSession.mode == mode
        val prefix = if (selected) "[x]" else "[ ]"
        return "$prefix ${mode.displayName}"
    }
}

