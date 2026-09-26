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
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.component.ShortcutRow
import io.github.fopwoc.mods.gtnhmeasurement.config.MeasurementConfig
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession

/**
 * The shortcuts that apply right now, in a box centred above the hotbar while measure mode is on.
 * [freecamReach] is the camera reach while a free camera is detached from the player, else null.
 */
class MeasurementShortcutHudOverlay(private val freecamReach: () -> Int?) : HudLayer("measure") {
    private var model by mutableStateOf<MeasurementShortcutHudModel?>(null)

    override val visible: Boolean
        get() {
            val client = ClientBackend.current
            return MeasurementSession.isActive &&
                MeasurementConfig.showShortcutHud &&
                !client.isScreenOpen &&
                !client.isHudHidden &&
                client.isInWorld
        }

    override fun beforeFrame() {
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
                    freecamReach = freecamReach(),
                )
            )
    }

    @Composable
    override fun Content() {
        val current = model ?: return
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(bottom = (HOTBAR_HEIGHT + MeasurementConfig.hudMargin).uu),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier =
                    Modifier.background(Color(0x7C000000))
                        .border(Color(0xAA4A4A56))
                        .padding((BOX_PADDING + BORDER).uu),
                verticalArrangement = VerticalArrangement.spacedBy(ROW_SPACING.uu),
            ) {
                Text(
                    text = current.title,
                    modifier = Modifier.padding(bottom = (TITLE_GAP - ROW_SPACING).uu),
                    style = TextStyle(color = MeasurementShortcutHudPalette.Title),
                )
                current.hints.forEach { hint ->
                    ShortcutRow(keys = hint.keys, action = hint.action, actionColor = hint.color)
                }
            }
        }
    }

    private companion object {
        /** The vanilla hotbar's height above the bottom of the screen. */
        const val HOTBAR_HEIGHT = 22
        const val BOX_PADDING = 5
        const val BORDER = 1
        const val ROW_SPACING = 2
        const val TITLE_GAP = 4
    }
}
