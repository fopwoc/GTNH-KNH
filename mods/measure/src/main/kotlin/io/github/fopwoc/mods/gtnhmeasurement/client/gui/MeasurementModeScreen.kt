package io.github.fopwoc.mods.gtnhmeasurement.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Spacer
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreen
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementSelectionState
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementOverlayPalette
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementShortcutScheme
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.OverlayVisualState
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession

@SideOnly(Side.CLIENT)
class MeasurementModeScreen : ComposeGuiScreen() {
    private val selectableModes = listOf(
        MeasurementMode.LINE,
        MeasurementMode.AREA,
        MeasurementMode.DISABLED
    )

    override fun doesGuiPauseGame(): Boolean = false

    @Composable
    override fun Content() {
        var selectedMode by remember {
            mutableStateOf(MeasurementSession.mode)
        }

        val activeColor = if (selectedMode.isEnabled) {
            MeasurementOverlayPalette.style(selectedMode, OverlayVisualState.NORMAL)
                .shapeColor(selectedMode)
        } else {
            Color.rgb(red = 0xFF, green = 0xAA, blue = 0xAA)
        }
        val footerText = if (selectedMode.isEnabled) {
            MeasurementShortcutScheme.footerText()
        } else {
            "Select a mode to enable measuring"
        }

        Box(modifier = Modifier().fillMaxSize()) {
            Column(
                modifier = Modifier()
                    .width(224.uu)
                    .padding(12.uu)
                    .background(Color(0xB0141418))
                    .border(Color(0xFF4A4A56))
                    .align(Alignment.Center),
                verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                horizontalAlignment = HorizontalAlignment.CENTER
            ) {
                Text(
                    text = "Measurement Mode",
                    modifier = Modifier().fillMaxWidth(),
                    style = TextStyle(
                        color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                        alignment = HorizontalAlignment.CENTER
                    )
                )
                Text(
                    text = "Current: ${selectedMode.displayName}",
                    modifier = Modifier().fillMaxWidth(),
                    style = TextStyle(
                        color = activeColor,
                        alignment = HorizontalAlignment.CENTER
                    )
                )
                Spacer(height = 4.uu)
                selectableModes.forEach { mode ->
                    Button(
                        text = buttonLabel(selectedMode, mode),
                        modifier = Modifier().fillMaxWidth(),
                        onClick = {
                            if (mode.isEnabled) {
                                MeasurementSession.switchTo(mode)
                            } else {
                                MeasurementSession.disable()
                                MeasurementSelectionState.clearTransientState()
                            }
                            selectedMode = MeasurementSession.mode
                        }
                    )
                }
                Spacer(height = 2.uu)
                Button(
                    text = "Close",
                    modifier = Modifier().fillMaxWidth(),
                    onClick = {
                        mc.displayGuiScreen(null)
                    }
                )
                Spacer(height = 4.uu)
                Text(
                    text = footerText,
                    modifier = Modifier().fillMaxWidth(),
                    style = TextStyle(
                        color = Color.rgb(red = 0xB8, green = 0xB8, blue = 0xB8),
                        alignment = HorizontalAlignment.CENTER,
                        wrap = true
                    )
                )
            }
        }
    }

    private fun buttonLabel(selectedMode: MeasurementMode, mode: MeasurementMode): String {
        val selected = selectedMode == mode
        val prefix = if (selected) "[x]" else "[ ]"
        return "$prefix ${mode.displayName}"
    }
}
