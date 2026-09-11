package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.page.editor

import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementSelectionState
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementShortcutScheme
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft

object MeasurementEditorRuntimeSnapshot {
  fun read(minecraft: Minecraft = Minecraft.getMinecraft()): MeasurementEditorModel {
    val selectedMode = MeasurementSession.mode
    val world = minecraft.theWorld
    val visibleCount =
        world?.provider?.dimensionId?.let(MeasurementSelectionState::measurementsForDimension)?.size

    return MeasurementEditorModel(
        selectedMode = selectedMode,
        footerText = if (selectedMode.isEnabled) MeasurementShortcutScheme.footerText() else "",
        modeBadgeText = "Mode · ${selectedMode.displayName}",
        contextLabel =
            when {
              world == null || visibleCount == null -> "No world loaded"
              else -> "Dim ${world.provider.dimensionId} · $visibleCount visible"
            },
    )
  }
}
