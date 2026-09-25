package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.hud.HudLayer
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudAnchor
import io.github.fopwoc.mods.framework.ui.compose.minecraft.HudRect
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.component.ShortcutRow
import io.github.fopwoc.mods.gtnhmeasurement.config.MeasurementConfig
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft

object MeasurementShortcutHudOverlay : HudLayer("measure") {
    private const val BOX_PADDING = 5
    private const val CHIP_PADDING_X = 4
    private const val CHIP_PADDING_Y = 2
    private const val ROW_GAP = 4
    private const val ROW_SPACING = 2
    private const val TITLE_GAP = 4

    private var model by mutableStateOf<MeasurementShortcutHudModel?>(null)
    private var bounds by mutableStateOf(HudRect.Zero)

    override val visible: Boolean
        get() {
            val client = Minecraft.getInstance()
            return MeasurementSession.isActive &&
                MeasurementConfig.showShortcutHud &&
                client.gui.screen() == null &&
                !client.gui.hud.isHidden &&
                ClientBackend.current.isInWorld
        }

    override fun beforeFrame() {
        val client = Minecraft.getInstance()
        val dimensionId = ClientBackend.current.currentDimensionId ?: return
        val hovered = MeasurementInteractionState.currentHoveredTarget?.block
        model =
            MeasurementShortcutHudResolver.resolve(
                MeasurementShortcutHudContext(
                    modeActive = MeasurementSession.isActive,
                    selectedMode = MeasurementSession.mode,
                    selectedMeasurementCount =
                        MeasurementSelectionState.selectedMeasurementsForDimension(dimensionId)
                            .size,
                    hoveredMeasurementCount =
                        hovered?.let(MeasurementSelectionState::measurementsContainingBlock)?.size
                            ?: 0,
                    hasDraftCreation = MeasurementSelectionState.hasActiveDraftCreation,
                    draftHasPreview = MeasurementSelectionState.draftSecond != null,
                    clipboardOperation = MeasurementSelectionState.activeClipboard?.operation,
                    pastePlacementActive = MeasurementSelectionState.isPastePlacementActive,
                    freecamReach =
                        ModernFreecamReach.reach.takeIf { ModernFreecamReach.isDetached },
                )
            )
        val current = model ?: return
        val font = client.font
        val rowHeight = font.lineHeight + CHIP_PADDING_Y * 2
        val rowWidths =
            current.hints.map { hint ->
                (if (hint.keys.isEmpty()) 0
                else font.width(hint.keys) + CHIP_PADDING_X * 2 + 2 + ROW_GAP) +
                    font.width(hint.action)
            }
        val boxWidth =
            maxOf(font.width(current.title), rowWidths.maxOrNull() ?: 0) + BOX_PADDING * 2 + 2
        val boxHeight =
            font.lineHeight +
                TITLE_GAP +
                current.hints.size * rowHeight +
                (current.hints.size - 1).coerceAtLeast(0) * ROW_SPACING +
                BOX_PADDING * 2 +
                2
        bounds =
            HudRect(
                width / 2 - boxWidth / 2,
                height - 22 - MeasurementConfig.hudMargin - boxHeight,
                boxWidth,
                boxHeight,
            )
    }

    @Composable
    override fun Content() {
        val current = model ?: return
        Box(modifier = Modifier.fillMaxSize()) {
            HudAnchor(bounds = bounds, contentAlignment = Alignment.TopStart) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(Color(0x7C000000))
                            .border(Color(0xAA4A4A56))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(BOX_PADDING.uu),
                        verticalArrangement = VerticalArrangement.spacedBy(ROW_SPACING.uu),
                    ) {
                        Text(
                            text = current.title,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(bottom = (TITLE_GAP - ROW_SPACING).uu),
                            style = TextStyle(color = MeasurementShortcutHudPalette.Title),
                        )
                        current.hints.forEach { hint ->
                            ShortcutRow(
                                keys = hint.keys,
                                action = hint.action,
                                actionColor = hint.color,
                            )
                        }
                    }
                }
            }
        }
    }
}
